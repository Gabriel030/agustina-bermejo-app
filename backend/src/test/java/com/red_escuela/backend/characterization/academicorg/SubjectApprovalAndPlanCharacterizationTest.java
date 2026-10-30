package com.red_escuela.backend.characterization.academicorg;

import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.CreateSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.RejectSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.GradeMode;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubjectApprovalAndPlanCharacterizationTest extends AcademicOrgCharacterizationSupport {

    private long createProgram(Person admin, String name) throws Exception {
        CreateProgramRequest request = new CreateProgramRequest(name, 3, null, null, 10, null, null, null, null, null);
        return idOf(postJson(admin, "/programs", request).andExpect(status().isCreated()));
    }

    private CreateProgramSubjectRequest mandatoryEntry(long subjectId) {
        return new CreateProgramSubjectRequest(
                subjectId, ProgramSubjectCategory.MANDATORY, 1, CurricularTerm.FIRST, 10, null, null, null);
    }

    @Test
    void administrativeCreatesSubjectAsDraftAndSubmitsItAndDirectorApprovesItAndItJoinsAProgramPlan() throws Exception {
        Person admin = persistPerson("acad.admin1", "72000001", "ADMINISTRATIVO");
        Person director = persistPerson("acad.director1", "72000002", "DIRECTOR");

        long subjectId = idOf(postJson(admin, "/subjects", completeSubjectRequest("Algebra I"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.name").value("Algebra I"))
                .andExpect(jsonPath("$.gradeMode").value("NUMERIC"))
                .andExpect(jsonPath("$.maxFinalExamAttempts").value(3)));

        postEmpty(admin, "/subjects/" + subjectId + "/submit-for-approval")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        postEmpty(director, "/subjects/" + subjectId + "/approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        long programId = createProgram(admin, "Tecnicatura Academica 1");
        postJson(admin, "/programs/" + programId + "/subjects", mandatoryEntry(subjectId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.programId").value(programId))
                .andExpect(jsonPath("$.subjectId").value(subjectId))
                .andExpect(jsonPath("$.category").value("MANDATORY"))
                .andExpect(jsonPath("$.curricularYear").value(1))
                .andExpect(jsonPath("$.credits").value(10));

        getAs(admin, "/programs/" + programId + "/subjects")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subjectId").value(subjectId));
        getAs(admin, "/subjects?status=ACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + subjectId + ")]").isNotEmpty());
    }

    @Test
    void onlyDirectorCanApproveAndAdministrativeCannot() throws Exception {
        Person admin = persistPerson("acad.admin2", "72000003", "ADMINISTRATIVO");
        long subjectId = idOf(postJson(admin, "/subjects", completeSubjectRequest("Fisica I")).andExpect(status().isCreated()));
        postEmpty(admin, "/subjects/" + subjectId + "/submit-for-approval").andExpect(status().isOk());

        postEmpty(admin, "/subjects/" + subjectId + "/approve").andExpect(status().isForbidden());
        getAs(admin, "/subjects/" + subjectId).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void directorRejectsWithReasonAndAdministrativeCanEditAndResubmit() throws Exception {
        Person admin = persistPerson("acad.admin3", "72000004", "ADMINISTRATIVO");
        Person director = persistPerson("acad.director3", "72000005", "DIRECTOR");
        long subjectId = idOf(postJson(admin, "/subjects", completeSubjectRequest("Quimica I")).andExpect(status().isCreated()));
        postEmpty(admin, "/subjects/" + subjectId + "/submit-for-approval").andExpect(status().isOk());

        postJson(director, "/subjects/" + subjectId + "/reject", new RejectSubjectRequest("Faltan contenidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Faltan contenidos"));

        postEmpty(admin, "/subjects/" + subjectId + "/submit-for-approval")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void subjectWithoutWeeklyHoursCannotBeSubmittedForApproval() throws Exception {
        Person admin = persistPerson("acad.admin4", "72000006", "ADMINISTRATIVO");
        CreateSubjectRequest incomplete = new CreateSubjectRequest(
                "Incompleta", TestSubjectCodes.next(), null, null, null, GradeMode.NUMERIC, null);
        long subjectId = idOf(postJson(admin, "/subjects", incomplete).andExpect(status().isCreated()));

        postEmpty(admin, "/subjects/" + subjectId + "/submit-for-approval")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INCOMPLETE_SUBJECT"));
    }

    @Test
    void approvingASubjectThatIsStillDraftIsRejectedAsInvalidTransition() throws Exception {
        Person admin = persistPerson("acad.admin5", "72000007", "ADMINISTRATIVO");
        Person director = persistPerson("acad.director5", "72000008", "DIRECTOR");
        long subjectId = idOf(postJson(admin, "/subjects", completeSubjectRequest("Historia")).andExpect(status().isCreated()));

        postEmpty(director, "/subjects/" + subjectId + "/approve")
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("SUBJECT_INVALID_TRANSITION"));
    }

    @Test
    void rejectedAndPendingSubjectsCannotBeAddedToAProgramPlan() throws Exception {
        Person admin = persistPerson("acad.admin6", "72000009", "ADMINISTRATIVO");
        Person director = persistPerson("acad.director6", "72000010", "DIRECTOR");
        long programId = createProgram(admin, "Tecnicatura Academica 2");
        long pendingId = idOf(postJson(admin, "/subjects", completeSubjectRequest("Pendiente")).andExpect(status().isCreated()));
        postEmpty(admin, "/subjects/" + pendingId + "/submit-for-approval").andExpect(status().isOk());
        long rejectedId = idOf(postJson(admin, "/subjects", completeSubjectRequest("Rechazada")).andExpect(status().isCreated()));
        postEmpty(admin, "/subjects/" + rejectedId + "/submit-for-approval").andExpect(status().isOk());
        postJson(director, "/subjects/" + rejectedId + "/reject", new RejectSubjectRequest("No")).andExpect(status().isOk());

        postJson(admin, "/programs/" + programId + "/subjects", mandatoryEntry(pendingId))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("SUBJECT_NOT_ASSIGNABLE_TO_PLAN"));
        postJson(admin, "/programs/" + programId + "/subjects", mandatoryEntry(rejectedId))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("SUBJECT_NOT_ASSIGNABLE_TO_PLAN"));
    }

    @Test
    void draftSubjectThatWasNeverApprovedCannotBeAddedToAProgramPlan() throws Exception {
        Person admin = persistPerson("acad.admin7", "72000011", "ADMINISTRATIVO");
        long programId = createProgram(admin, "Tecnicatura Academica 3");
        long draftId = idOf(postJson(admin, "/subjects", completeSubjectRequest("Borrador")).andExpect(status().isCreated()));

        postJson(admin, "/programs/" + programId + "/subjects", mandatoryEntry(draftId))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("SUBJECT_NOT_ASSIGNABLE_TO_PLAN"));
        getAs(admin, "/programs/" + programId + "/subjects")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void addingTheSameSubjectTwiceToAPlanIsRejected() throws Exception {
        Person admin = persistPerson("acad.admin8", "72000012", "ADMINISTRATIVO");
        long programId = createProgram(admin, "Tecnicatura Academica 4");
        Person director = persistPerson("acad.director8", "72000015", "DIRECTOR");
        long subjectId = idOf(postJson(admin, "/subjects", completeSubjectRequest("Repetida")).andExpect(status().isCreated()));
        postEmpty(admin, "/subjects/" + subjectId + "/submit-for-approval").andExpect(status().isOk());
        postEmpty(director, "/subjects/" + subjectId + "/approve").andExpect(status().isOk());
        postJson(admin, "/programs/" + programId + "/subjects", mandatoryEntry(subjectId)).andExpect(status().isCreated());

        postJson(admin, "/programs/" + programId + "/subjects", mandatoryEntry(subjectId))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("PROGRAM_SUBJECT_ALREADY_EXISTS"));
    }

    @Test
    void duplicateSubjectNameIsRejectedWithSubjectAlreadyExists() throws Exception {
        Person admin = persistPerson("acad.admin9", "72000013", "ADMINISTRATIVO");
        postJson(admin, "/subjects", completeSubjectRequest("Nombre Unico X")).andExpect(status().isCreated());

        postJson(admin, "/subjects", completeSubjectRequest("Nombre Unico X"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("SUBJECT_ALREADY_EXISTS"));
    }

    @Test
    void teacherCannotCreateSubjectOrProgramPlanEntries() throws Exception {
        Person teacher = persistPerson("acad.teacher1", "72000014", "DOCENTE");

        postJson(teacher, "/subjects", completeSubjectRequest("Prohibida")).andExpect(status().isForbidden());
        postJson(teacher, "/programs/1/subjects", mandatoryEntry(1)).andExpect(status().isForbidden());
    }
}
