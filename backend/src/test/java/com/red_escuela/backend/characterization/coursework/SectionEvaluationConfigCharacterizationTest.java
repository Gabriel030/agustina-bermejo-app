package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.evaluation.dto.RejectSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.dto.UpdateSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SectionEvaluationConfigCharacterizationTest extends CourseworkCharacterizationSupport {

    private static final LocalDate TERM_START = LocalDate.now().plusYears(5);

    private Classroom classroomWithDraftConfig(String key, String dniBase, InstructorRole role, LocalDateTime termStart) {
        Classroom classroom = persistClassroom(key, dniBase, persistScheme(2, true, null), null, 10, 10, role);
        SubjectSection section = classroom.section();
        section.getTerm().setStartDate(termStart);
        section.getTerm().setEndDate(termStart.plusDays(150));
        SectionEvaluationConfig config = new SectionEvaluationConfig();
        config.setSubjectSection(section);
        config.setStatus(SectionEvaluationConfigStatus.DRAFT);
        entityManager.persist(config);
        entityManager.flush();
        return classroom;
    }

    private Classroom classroomWithDraftConfig(String key, String dniBase) {
        return classroomWithDraftConfig(key, dniBase, InstructorRole.LEAD, TERM_START.atStartOfDay());
    }

    private UpdateSectionEvaluationConfigRequest proposal() {
        return new UpdateSectionEvaluationConfigRequest(
                1, TERM_START.plusDays(20), null, null,
                TERM_START.plusDays(30), TERM_START.plusDays(60), null, null,
                TERM_START.plusDays(100), TERM_START.plusDays(110));
    }

    private String configPath(Classroom classroom) {
        return "/me/section-evaluation-configs/" + classroom.section().getId();
    }

    @Test
    void leadTeacherSavesDraftSubmitsItAndDirectorsAreNotifiedThenDirectorApprovesAndTheTeacherIsNotified() throws Exception {
        Classroom classroom = classroomWithDraftConfig("cfg1", "7800001");
        Person director = persistPerson("cfg1.director", "78000017", "DIRECTOR");
        long sectionId = classroom.section().getId();

        putJson(classroom.teacher(), configPath(classroom), proposal())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.tpCount").value(1))
                .andExpect(jsonPath("$.exam1Date").value(TERM_START.plusDays(30).toString()))
                .andExpect(jsonPath("$.finalCall1Date").value(TERM_START.plusDays(100).toString()));
        postEmpty(classroom.teacher(), configPath(classroom) + "/submit")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        getAs(director, "/me/notifications")
                .andExpect(jsonPath("$[?(@.type=='SECTION_EVAL_CONFIG_PROPOSED')]").isNotEmpty());
        assertThat(countRows("ExamBoard", "e.subject.id = " + classroom.subject().getId())).isZero();

        postEmpty(director, "/section-evaluation-configs/" + sectionId + "/approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedByName").value("Nombre Apellido 78000017"));

        getAs(classroom.teacher(), "/me/notifications")
                .andExpect(jsonPath("$[?(@.type=='SECTION_EVAL_CONFIG_APPROVED')]").isNotEmpty());
        assertThat(countRows("ExamBoard", "e.subject.id = " + classroom.subject().getId())).isEqualTo(2);
        getAs(classroom.teacher(), configPath(classroom))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void directorRejectsWithReasonTeacherIsNotifiedAndCanEditAndResubmitClearingTheReason() throws Exception {
        Classroom classroom = classroomWithDraftConfig("cfg2", "7800002");
        Person director = persistPerson("cfg2.director", "78000027", "DIRECTOR");
        putJson(classroom.teacher(), configPath(classroom), proposal()).andExpect(status().isOk());
        postEmpty(classroom.teacher(), configPath(classroom) + "/submit").andExpect(status().isOk());

        postJson(director, "/section-evaluation-configs/" + classroom.section().getId() + "/reject",
                new RejectSectionEvaluationConfigRequest("Fechas muy juntas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Fechas muy juntas"));
        getAs(classroom.teacher(), "/me/notifications")
                .andExpect(jsonPath("$[?(@.type=='SECTION_EVAL_CONFIG_REJECTED')]").isNotEmpty());

        putJson(classroom.teacher(), configPath(classroom), proposal()).andExpect(status().isOk());
        postEmpty(classroom.teacher(), configPath(classroom) + "/submit")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
        assertThat(countRows("ExamBoard", "e.subject.id = " + classroom.subject().getId())).isZero();
    }

    @Test
    void administrativeCannotApproveNorRejectAndDirectorApprovalLocksTheConfig() throws Exception {
        Classroom classroom = classroomWithDraftConfig("cfg3", "7800003");
        Person admin = persistPerson("cfg3.admin", "78000037", "ADMINISTRATIVO");
        Person director = persistPerson("cfg3.director", "78000038", "DIRECTOR");
        putJson(classroom.teacher(), configPath(classroom), proposal()).andExpect(status().isOk());
        postEmpty(classroom.teacher(), configPath(classroom) + "/submit").andExpect(status().isOk());

        postEmpty(admin, "/section-evaluation-configs/" + classroom.section().getId() + "/approve")
                .andExpect(status().isForbidden());
        postJson(admin, "/section-evaluation-configs/" + classroom.section().getId() + "/reject",
                new RejectSectionEvaluationConfigRequest("No corresponde"))
                .andExpect(status().isForbidden());
        getAs(director, "/section-evaluation-configs/" + classroom.section().getId())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        postEmpty(director, "/section-evaluation-configs/" + classroom.section().getId() + "/approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        postEmpty(director, "/section-evaluation-configs/" + classroom.section().getId() + "/approve")
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("INVALID_SECTION_EVALUATION_CONFIG_TRANSITION"));
        putJson(classroom.teacher(), configPath(classroom), proposal())
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("INVALID_SECTION_EVALUATION_CONFIG_TRANSITION"));
    }

    @Test
    void approvingADraftThatWasNeverSubmittedIsRejected() throws Exception {
        Classroom classroom = classroomWithDraftConfig("cfg4", "7800004");
        Person director = persistPerson("cfg4.director", "78000047", "DIRECTOR");

        postEmpty(director, "/section-evaluation-configs/" + classroom.section().getId() + "/approve")
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("INVALID_SECTION_EVALUATION_CONFIG_TRANSITION"));
    }

    @Test
    void draftDatesOutsideTheTermAreRejectedAndHolidaysAreRejected() throws Exception {
        Classroom classroom = classroomWithDraftConfig("cfg5", "7800005");
        LocalDate beforeTerm = TERM_START.minusDays(1);

        putJson(classroom.teacher(), configPath(classroom),
                new UpdateSectionEvaluationConfigRequest(null, null, null, null, beforeTerm, null, null, null, null, null))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("EVALUATION_DATE_OUTSIDE_TERM"));

        classroom.section().getTerm().setStartDate(LocalDateTime.of(2026, 11, 1, 0, 0));
        classroom.section().getTerm().setEndDate(LocalDateTime.of(2026, 12, 30, 0, 0));
        entityManager.flush();
        putJson(classroom.teacher(), configPath(classroom),
                new UpdateSectionEvaluationConfigRequest(null, null, null, null, LocalDate.of(2026, 11, 20), null, null, null, null, null))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("EVALUATION_DATE_ON_HOLIDAY"));
    }

    @Test
    void submissionIsRejectedOnceTheTermHasStartedAndStillAllowedAfterARejection() throws Exception {
        Classroom classroom = classroomWithDraftConfig("cfg6", "7800006", InstructorRole.LEAD, LocalDateTime.now().minusDays(10));

        postEmpty(classroom.teacher(), configPath(classroom) + "/submit")
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("SUBMISSION_WINDOW_CLOSED"));
        getAs(classroom.teacher(), configPath(classroom))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.canSubmit").value(false));
    }

    @Test
    void onlyTheLeadInstructorOfTheSectionCanSaveOrSubmitTheDraft() throws Exception {
        Classroom assistantRoom = classroomWithDraftConfig("cfg7", "7800007", InstructorRole.ASSISTANT, TERM_START.atStartOfDay());
        Classroom leadRoom = classroomWithDraftConfig("cfg7b", "7800008");

        putJson(assistantRoom.teacher(), configPath(assistantRoom), proposal()).andExpect(status().isForbidden());
        postEmpty(assistantRoom.teacher(), configPath(assistantRoom) + "/submit").andExpect(status().isForbidden());
        putJson(leadRoom.teacher(), configPath(assistantRoom), proposal()).andExpect(status().isForbidden());
        getAs(assistantRoom.teacher(), configPath(assistantRoom)).andExpect(status().isOk());
    }

    @Test
    void teachersCannotApproveOrRejectAndStaffListsConfigsByStatus() throws Exception {
        Classroom classroom = classroomWithDraftConfig("cfg8", "7800009");
        Person director = persistPerson("cfg8.director", "78000097", "DIRECTOR");

        postEmpty(classroom.teacher(), "/section-evaluation-configs/" + classroom.section().getId() + "/approve")
                .andExpect(status().isForbidden());
        getAs(director, "/section-evaluation-configs?status=DRAFT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.subjectSectionId==" + classroom.section().getId() + ")]").isNotEmpty());
        getAs(classroom.teacher(), "/me/section-evaluation-configs")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
