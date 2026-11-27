package com.red_escuela.backend.characterization.academicorg;

import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DraftSubjectRulesTest extends AcademicOrgCharacterizationSupport {

    private long createProgram(Person admin, String name) throws Exception {
        CreateProgramRequest request = new CreateProgramRequest(name, 3, com.red_escuela.backend.shared.domain.TestMoney.ars("1000"), com.red_escuela.backend.shared.domain.TestMoney.ars("1000"), 10, null, null, null, null, null);
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
