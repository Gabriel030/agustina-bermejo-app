package com.red_escuela.backend.characterization.academicorg;

import com.red_escuela.backend.academicstructure.dto.CreateSectionRequest;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SectionCreationCharacterizationTest extends AcademicOrgCharacterizationSupport {

    private long createActiveSubject(Person admin, Person director, String name) throws Exception {
        long subjectId = idOf(postJson(admin, "/subjects", completeSubjectRequest(name)).andExpect(status().isCreated()));
        postEmpty(admin, "/subjects/" + subjectId + "/submit-for-approval").andExpect(status().isOk());
        postEmpty(director, "/subjects/" + subjectId + "/approve").andExpect(status().isOk());
        return subjectId;
    }

    @Test
    void preceptorCreatesSectionAndAnInitialEvaluationConfigDraftIsCreatedInTheSameCall() throws Exception {
        Person admin = persistPerson("sect.admin1", "73000001", "ADMINISTRATIVO");
        Person director = persistPerson("sect.director1", "73000002", "DIRECTOR");
        Person preceptor = persistPerson("sect.preceptor1", "73000003", "PRECEPTOR");
        Term term = persistTerm();
        long subjectId = createActiveSubject(admin, director, "Programacion I");

        long sectionId = idOf(postJson(preceptor, "/subjects/" + subjectId + "/sections",
                new CreateSectionRequest("Comision A", term.getId(), "Lunes 18hs", 30, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Comision A"))
                .andExpect(jsonPath("$.subjectId").value(subjectId))
                .andExpect(jsonPath("$.termId").value(term.getId()))
                .andExpect(jsonPath("$.capacity").value(30))
                .andExpect(jsonPath("$.status").value("ACTIVE")));

        assertThat(countRows("SectionEvaluationConfig", "e.subjectSection.id = " + sectionId)).isEqualTo(1);
        getAs(director, "/section-evaluation-configs/" + sectionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.subjectSectionId").value(sectionId))
                .andExpect(jsonPath("$.subjectName").value("Programacion I"))
                .andExpect(jsonPath("$.sectionName").value("Comision A"))
                .andExpect(jsonPath("$.evaluationSchemeName").doesNotExist());
        getAs(director, "/section-evaluation-configs?status=DRAFT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.subjectSectionId==" + sectionId + ")]").isNotEmpty());
    }

    @Test
    void eachCreatedSectionGetsItsOwnDraftConfig() throws Exception {
        Person admin = persistPerson("sect.admin2", "73000004", "ADMINISTRATIVO");
        Person director = persistPerson("sect.director2", "73000005", "DIRECTOR");
        Term term = persistTerm();
        long subjectId = createActiveSubject(admin, director, "Programacion II");

        long first = idOf(postJson(admin, "/subjects/" + subjectId + "/sections",
                new CreateSectionRequest("Comision A", term.getId(), null, 20, null)).andExpect(status().isCreated()));
        long second = idOf(postJson(admin, "/subjects/" + subjectId + "/sections",
                new CreateSectionRequest("Comision B", term.getId(), null, 20, null)).andExpect(status().isCreated()));

        assertThat(first).isNotEqualTo(second);
        assertThat(countRows("SectionEvaluationConfig", "e.subjectSection.id in (" + first + ", " + second + ")"))
                .isEqualTo(2);
    }

    @Test
    void sectionCannotBeCreatedOnASubjectThatIsStillDraftAndNoConfigDraftIsCreated() throws Exception {
        Person admin = persistPerson("sect.admin3", "73000006", "ADMINISTRATIVO");
        Term term = persistTerm();
        long subjectId = idOf(postJson(admin, "/subjects", completeSubjectRequest("Materia Borrador"))
                .andExpect(status().isCreated()));

        postJson(admin, "/subjects/" + subjectId + "/sections",
                new CreateSectionRequest("Comision Z", term.getId(), null, 10, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBJECT_NOT_OPENABLE_FOR_SECTIONS"));

        assertThat(countRows("SubjectSection", "e.subject.id = " + subjectId)).isZero();
        assertThat(countRows("SectionEvaluationConfig", "e.subjectSection.subject.id = " + subjectId)).isZero();
    }

    @Test
    void sectionWithUnknownTermOrSubjectOrSchemeIsRejected() throws Exception {
        Person admin = persistPerson("sect.admin4", "73000007", "ADMINISTRATIVO");
        Person director = persistPerson("sect.director4", "73000008", "DIRECTOR");
        Term term = persistTerm();
        long subjectId = createActiveSubject(admin, director, "Programacion III");

        postJson(admin, "/subjects/" + subjectId + "/sections",
                new CreateSectionRequest("Comision A", 999999L, null, 10, null))
                .andExpect(status().isNotFound());
        postJson(admin, "/subjects/999999/sections",
                new CreateSectionRequest("Comision A", term.getId(), null, 10, null))
                .andExpect(status().isNotFound());
        postJson(admin, "/subjects/" + subjectId + "/sections",
                new CreateSectionRequest("Comision A", term.getId(), null, 10, 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void sectionRequestWithNonPositiveCapacityIsRejectedAndTeacherCannotCreateSections() throws Exception {
        Person admin = persistPerson("sect.admin5", "73000009", "ADMINISTRATIVO");
        Person teacher = persistPerson("sect.teacher5", "73000010", "DOCENTE");
        Term term = persistTerm();
        long subjectId = idOf(postJson(admin, "/subjects", completeSubjectRequest("Programacion IV"))
                .andExpect(status().isCreated()));

        postJson(admin, "/subjects/" + subjectId + "/sections",
                new CreateSectionRequest("Comision A", term.getId(), null, 0, null)).andExpect(status().isBadRequest());
        postJson(teacher, "/subjects/" + subjectId + "/sections",
                new CreateSectionRequest("Comision A", term.getId(), null, 10, null)).andExpect(status().isForbidden());
        assertThat(countRows("SubjectSection", "e.subject.id = " + subjectId)).isZero();
    }
}
