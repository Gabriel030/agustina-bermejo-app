package com.red_escuela.backend.characterization.academicorg;

import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.CreateSectionRequest;
import com.red_escuela.backend.academicstructure.dto.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DraftSubjectRulesTest extends AcademicOrgCharacterizationSupport {

    private long createProgram(Person admin, String name) throws Exception {
        CreateProgramRequest request = new CreateProgramRequest(name, 3, null, null, 10, null, null, null, null, null);
        return idOf(postJson(admin, "/programs", request).andExpect(status().isCreated()));
    }

    private CreateProgramSubjectRequest entry(long subjectId) {
        return new CreateProgramSubjectRequest(
                subjectId, ProgramSubjectCategory.MANDATORY, 1, CurricularTerm.FIRST, 10, null, null, null);
    }

    private long createDraft(Person admin, String name) throws Exception {
        return idOf(postJson(admin, "/subjects", completeSubjectRequest(name)).andExpect(status().isCreated()));
    }

    private long createActive(Person admin, Person director, String name) throws Exception {
        long subjectId = createDraft(admin, name);
        postEmpty(admin, "/subjects/" + subjectId + "/submit-for-approval").andExpect(status().isOk());
        postEmpty(director, "/subjects/" + subjectId + "/approve").andExpect(status().isOk());
        return subjectId;
    }

    @Test
    void draftSubjectCannotJoinAPlanButBecomesAssignableOnceApproved() throws Exception {
        Person admin = persistPerson("draft.admin1", "74000001", "ADMINISTRATIVO");
        Person director = persistPerson("draft.director1", "74000002", "DIRECTOR");
        long programId = createProgram(admin, "Tecnicatura Draft 1");
        long subjectId = createDraft(admin, "Materia Draft 1");

        postJson(admin, "/programs/" + programId + "/subjects", entry(subjectId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBJECT_NOT_ASSIGNABLE_TO_PLAN"));

        postEmpty(admin, "/subjects/" + subjectId + "/submit-for-approval").andExpect(status().isOk());
        postEmpty(director, "/subjects/" + subjectId + "/approve").andExpect(status().isOk());
        postJson(admin, "/programs/" + programId + "/subjects", entry(subjectId)).andExpect(status().isCreated());
    }

    @Test
    void sectionCannotBeOpenedOnADraftSubjectByAnyStaffRole() throws Exception {
        Person admin = persistPerson("draft.admin3", "74000005", "ADMINISTRATIVO");
        Person preceptor = persistPerson("draft.preceptor3", "74000006", "PRECEPTOR");
        Term term = persistTerm();
        long subjectId = createDraft(admin, "Materia Draft 3");
        CreateSectionRequest request = new CreateSectionRequest("Comision A", term.getId(), null, 10, null);

        postJson(admin, "/subjects/" + subjectId + "/sections", request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBJECT_NOT_OPENABLE_FOR_SECTIONS"));
        postJson(preceptor, "/subjects/" + subjectId + "/sections", request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBJECT_NOT_OPENABLE_FOR_SECTIONS"));
        assertThat(countRows("SubjectSection", "e.subject.id = " + subjectId)).isZero();
    }

    @Test
    void sectionCanBeOpenedOnceTheSubjectIsApproved() throws Exception {
        Person admin = persistPerson("draft.admin4", "74000007", "ADMINISTRATIVO");
        Person director = persistPerson("draft.director4", "74000008", "DIRECTOR");
        Term term = persistTerm();
        long subjectId = createActive(admin, director, "Materia Draft 4");

        postJson(admin, "/subjects/" + subjectId + "/sections",
                new CreateSectionRequest("Comision A", term.getId(), null, 10, null))
                .andExpect(status().isCreated());
    }

    @Test
    void programSubjectListExposesTheSubjectStatus() throws Exception {
        Person admin = persistPerson("draft.admin5", "74000009", "ADMINISTRATIVO");
        Person director = persistPerson("draft.director5", "74000010", "DIRECTOR");
        long programId = createProgram(admin, "Tecnicatura Draft 5");
        long subjectId = createActive(admin, director, "Materia Draft 5");
        postJson(admin, "/programs/" + programId + "/subjects", entry(subjectId)).andExpect(status().isCreated());

        getAs(admin, "/programs/" + programId + "/subjects")
                .andExpect(jsonPath("$[0].subjectStatus").value("ACTIVE"));
    }
}
