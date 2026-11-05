package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinalExamRegularStudentRuleTest extends CourseworkCharacterizationSupport {

    private Classroom classroom(String key, String dniBase) {
        return persistClassroom(key, dniBase, persistScheme(2, true, null), null, 10, 10, InstructorRole.LEAD);
    }

    private ResultActions load(Person actor, Classroom classroom, GradeItemType type, int sequence, String score) throws Exception {
        return postJson(actor, "/section-enrollments/" + classroom.enrollment().getId() + "/grade-items",
                new CreateGradeItemRequest(type, sequence, null, LocalDateTime.of(2026, 6, 1, 10, 0),
                        new BigDecimal(score), null, null));
    }

    private void makeRegular(Classroom classroom) throws Exception {
        load(classroom.teacher(), classroom, GradeItemType.EXAM, 1, "6").andExpect(status().isCreated());
        load(classroom.teacher(), classroom, GradeItemType.EXAM, 2, "6").andExpect(status().isCreated());
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.REGULAR);
    }

    private long finalItems(Classroom classroom) {
        return countRows("GradeItem", "e.programSubjectSectionEnrollment.id = " + classroom.enrollment().getId()
                + " and e.type = com.red_escuela.backend.evaluation.entity.GradeItemType.FINAL");
    }

    @Test
    void regularStudentCanSitTheFinal() throws Exception {
        Classroom classroom = classroom("fin1", "7900001");
        makeRegular(classroom);

        load(classroom.teacher(), classroom, GradeItemType.FINAL, 1, "8").andExpect(status().isCreated());

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.APPROVED);
    }

    @Test
    void studentInProgressWhoNeverBecameRegularIsRejectedWithABusinessErrorAndNothingIsStored() throws Exception {
        Classroom classroom = classroom("fin2", "7900002");

        load(classroom.teacher(), classroom, GradeItemType.FINAL, 1, "9")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FINAL_EXAM_REQUIRES_REGULAR_STUDENT"))
                .andExpect(jsonPath("$.violations[0].code").value("STUDENT_NOT_REGULAR_FOR_FINAL_EXAM"));

        assertThat(finalItems(classroom)).isZero();
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.IN_PROGRESS);
    }

    @Test
    void studentWithOnlyOneExamLoadedIsStillNotRegularAndCannotSitTheFinal() throws Exception {
        Classroom classroom = classroom("fin3", "7900003");
        load(classroom.teacher(), classroom, GradeItemType.EXAM, 1, "8").andExpect(status().isCreated());

        load(classroom.teacher(), classroom, GradeItemType.FINAL, 1, "8")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FINAL_EXAM_REQUIRES_REGULAR_STUDENT"));
    }

    @Test
    void studentWhoFailedTheCourseworkCannotSitTheFinal() throws Exception {
        Classroom classroom = classroom("fin4", "7900004");
        load(classroom.teacher(), classroom, GradeItemType.EXAM, 1, "2").andExpect(status().isCreated());
        load(classroom.teacher(), classroom, GradeItemType.EXAM, 2, "3").andExpect(status().isCreated());
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.FAILED);

        load(classroom.teacher(), classroom, GradeItemType.FINAL, 1, "9")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FINAL_EXAM_REQUIRES_REGULAR_STUDENT"));
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.FAILED);
    }

    @Test
    void afterTheOnlyAttemptIsFailedASecondFinalIsRejectedBecauseTheStudentIsNoLongerRegular() throws Exception {
        Classroom classroom = classroom("fin5", "7900005");
        makeRegular(classroom);
        load(classroom.teacher(), classroom, GradeItemType.FINAL, 1, "3").andExpect(status().isCreated());
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.FAILED);

        load(classroom.teacher(), classroom, GradeItemType.FINAL, 2, "9")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FINAL_EXAM_REQUIRES_REGULAR_STUDENT"));

        assertThat(finalItems(classroom)).isEqualTo(1);
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.FAILED);
    }

    @Test
    void studentRegularWithAttemptsRemainingCanSitASecondFinal() throws Exception {
        Classroom classroom = classroom("fin6", "7900006");
        classroom.subject().setMaxFinalExamAttempts(2);
        entityManager.flush();
        makeRegular(classroom);
        load(classroom.teacher(), classroom, GradeItemType.FINAL, 1, "3").andExpect(status().isCreated());

        load(classroom.teacher(), classroom, GradeItemType.FINAL, 2, "7").andExpect(status().isCreated());

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.APPROVED);
    }

    @Test
    void examLoadingDoesNotRequireTheStudentToBeRegular() throws Exception {
        Classroom classroom = classroom("fin7", "7900007");

        load(classroom.teacher(), classroom, GradeItemType.EXAM, 1, "5").andExpect(status().isCreated());
    }

    @Test
    void correctingAnApprovedFinalToAFailingGradeMakesTheEnrollmentFailedAndClearsTheFinalScore() throws Exception {
        Classroom classroom = classroom("fin8", "7900008");
        makeRegular(classroom);
        load(classroom.teacher(), classroom, GradeItemType.FINAL, 1, "8").andExpect(status().isCreated());
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.APPROVED);

        load(classroom.teacher(), classroom, GradeItemType.FINAL, 1, "2").andExpect(status().isCreated());

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.FAILED);
        assertThat(classroom.enrollment().getFinalScore()).isNull();
        assertThat(finalItems(classroom)).isEqualTo(1);
    }

    @Test
    void correctingAFailedFinalToAPassingGradeMakesTheEnrollmentApproved() throws Exception {
        Classroom classroom = classroom("fin9", "7900009");
        makeRegular(classroom);
        load(classroom.teacher(), classroom, GradeItemType.FINAL, 1, "3").andExpect(status().isCreated());
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.FAILED);

        load(classroom.teacher(), classroom, GradeItemType.FINAL, 1, "9").andExpect(status().isCreated());

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(classroom.enrollment().getFinalScore().value()).isEqualByComparingTo("9");
    }

    @Test
    void correctingAnApprovedFinalToAFailingGradeWithAttemptsLeftReturnsTheStudentToRegular() throws Exception {
        Classroom classroom = classroom("fin10", "7900010");
        classroom.subject().setMaxFinalExamAttempts(2);
        entityManager.flush();
        makeRegular(classroom);
        load(classroom.teacher(), classroom, GradeItemType.FINAL, 1, "8").andExpect(status().isCreated());

        load(classroom.teacher(), classroom, GradeItemType.FINAL, 1, "2").andExpect(status().isCreated());

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.REGULAR);
        assertThat(classroom.enrollment().getFinalScore()).isNull();
    }
}
