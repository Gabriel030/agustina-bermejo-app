package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.entity.InstructorRole;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GradeProgressionAndGraduationCharacterizationTest extends CourseworkCharacterizationSupport {

    private Classroom withFinal(String key, String dniBase, int programCredits) {
        return persistClassroom(key, dniBase, persistScheme(2, true, null), null, programCredits, 10, InstructorRole.LEAD);
    }

    private void load(Classroom classroom, GradeItemType type, int sequence, String score) throws Exception {
        loadAs(classroom.teacher(), classroom, type, sequence, score).andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions loadAs(
            Person actor, Classroom classroom, GradeItemType type, int sequence, String score) throws Exception {
        return postJson(actor, "/section-enrollments/" + classroom.enrollment().getId() + "/grade-items",
                new CreateGradeItemRequest(type, sequence, null, LocalDateTime.of(2026, 6, 1, 10, 0),
                        new BigDecimal(score), null, null));
    }

    @Test
    void examsMakeTheStudentRegularAndTheFinalApprovesTheSubjectAndRequestsGraduationAutomatically() throws Exception {
        Classroom classroom = withFinal("grade1", "7700001", 10);
        Person director = persistPerson("grade1.director", "77000017", "DIRECTOR");

        load(classroom, GradeItemType.EXAM, 1, "7");
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        load(classroom, GradeItemType.EXAM, 2, "5");
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.REGULAR);
        assertThat(countRows("GraduationRequest", "e.programEnrollment.id = " + classroom.programEnrollment().getId())).isZero();

        load(classroom, GradeItemType.FINAL, 1, "8");

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(classroom.enrollment().getFinalScore().value()).isEqualByComparingTo("8");
        assertThat(classroom.enrollment().getFinalGradeType()).isEqualTo(FinalGradeType.FINAL_EXAM);
        assertThat(countRows("GraduationRequest", "e.programEnrollment.id = " + classroom.programEnrollment().getId()
                + " and e.status = 'REQUESTED'")).isEqualTo(1);
        getAs(director, "/graduation-requests?status=REQUESTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.programEnrollmentId==" + classroom.programEnrollment().getId() + ")]").isNotEmpty());
        getAs(director, "/program-enrollments/" + classroom.programEnrollment().getId() + "/graduation-requests")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("REQUESTED"));
    }

    @Test
    void approvingASubjectDoesNotRequestGraduationWhileCreditsRemainPending() throws Exception {
        Classroom classroom = withFinal("grade2", "7700002", 20);

        load(classroom, GradeItemType.EXAM, 1, "7");
        load(classroom, GradeItemType.EXAM, 2, "7");
        load(classroom, GradeItemType.FINAL, 1, "9");

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(countRows("GraduationRequest", "e.programEnrollment.id = " + classroom.programEnrollment().getId())).isZero();
    }

    @Test
    void examAverageBelowTheRegularThresholdFailsTheSubject() throws Exception {
        Classroom classroom = withFinal("grade3", "7700003", 10);

        load(classroom, GradeItemType.EXAM, 1, "2");
        load(classroom, GradeItemType.EXAM, 2, "3");

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.FAILED);
        assertThat(classroom.enrollment().getFinalScore()).isNull();
    }

    @Test
    void failedFinalWithTheDefaultSingleAttemptFailsTheSubject() throws Exception {
        Classroom classroom = withFinal("grade4", "7700004", 10);
        load(classroom, GradeItemType.EXAM, 1, "6");
        load(classroom, GradeItemType.EXAM, 2, "6");
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.REGULAR);

        load(classroom, GradeItemType.FINAL, 1, "3");

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.FAILED);
        assertThat(countRows("GraduationRequest", "e.programEnrollment.id = " + classroom.programEnrollment().getId())).isZero();
    }

    @Test
    void failedFinalKeepsTheStudentRegularWhileAttemptsRemainAndAPassingSecondFinalApproves() throws Exception {
        Classroom classroom = withFinal("grade4b", "7700012", 10);
        classroom.subject().setMaxFinalExamAttempts(2);
        entityManager.flush();
        load(classroom, GradeItemType.EXAM, 1, "6");
        load(classroom, GradeItemType.EXAM, 2, "6");

        load(classroom, GradeItemType.FINAL, 1, "3");
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.REGULAR);

        load(classroom, GradeItemType.FINAL, 2, "7");
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(classroom.enrollment().getFinalScore().value()).isEqualByComparingTo("7");
    }

    @Test
    void promotionThresholdOnAllExamsApprovesWithoutFinalAndRequestsGraduation() throws Exception {
        Classroom classroom = persistClassroom("grade5", "7700005", persistScheme(2, true, 7.0), null, 10, 10, InstructorRole.LEAD);

        load(classroom, GradeItemType.EXAM, 1, "8");
        load(classroom, GradeItemType.EXAM, 2, "9");

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(classroom.enrollment().getFinalGradeType()).isEqualTo(FinalGradeType.PROMOTION);
        assertThat(classroom.enrollment().getFinalScore().value()).isEqualByComparingTo("8.5");
        assertThat(countRows("GraduationRequest", "e.programEnrollment.id = " + classroom.programEnrollment().getId())).isEqualTo(1);
    }

    @Test
    void finalIsRejectedWhenTheStudentNeverBecameRegularAndTheSubjectIsNotApproved() throws Exception {
        Classroom classroom = withFinal("grade6", "7700006", 10);

        loadAs(classroom.teacher(), classroom, GradeItemType.FINAL, 1, "8")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FINAL_EXAM_REQUIRES_REGULAR_STUDENT"));

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        assertThat(classroom.enrollment().getFinalGradeType()).isNull();
        assertThat(countRows("GradeItem", "e.programSubjectSectionEnrollment.id = " + classroom.enrollment().getId())).isZero();
    }

    @Test
    void loadingTheSameTypeAndSequenceAgainReplacesTheScoreInsteadOfDuplicatingTheItem() throws Exception {
        Classroom classroom = withFinal("grade7", "7700007", 10);
        Person preceptor = persistPerson("grade7.preceptor", "77000077", "PRECEPTOR");

        load(classroom, GradeItemType.EXAM, 1, "4");
        load(classroom, GradeItemType.EXAM, 1, "9");

        getAs(preceptor, "/section-enrollments/" + classroom.enrollment().getId() + "/grade-items")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("EXAM"))
                .andExpect(jsonPath("$[0].score").value(9.0));
    }

    @Test
    void gradeLoadingIsRestrictedToTeachersOfTheSectionWithAValidScoreAndAnAssignedScheme() throws Exception {
        Classroom classroom = withFinal("grade8", "7700008", 10);
        Classroom other = withFinal("grade8b", "7700009", 10);
        Classroom noScheme = persistClassroom("grade8c", "7700010", null, null, 10, 10, InstructorRole.LEAD);
        Person preceptor = persistPerson("grade8.preceptor", "77000087", "PRECEPTOR");

        loadAs(other.teacher(), classroom, GradeItemType.EXAM, 1, "7")
                .andExpect(status().isForbidden());
        loadAs(preceptor, classroom, GradeItemType.EXAM, 1, "7").andExpect(status().isForbidden());
        loadAs(classroom.teacher(), classroom, GradeItemType.EXAM, 1, "11").andExpect(status().is4xxClientError());
        loadAs(noScheme.teacher(), noScheme, GradeItemType.EXAM, 1, "7")
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("NO_EVALUATION_SCHEME"));
        assertThat(countRows("GradeItem", "e.programSubjectSectionEnrollment.id = " + classroom.enrollment().getId())).isZero();
    }

    @Test
    void studentWhoIsLibreByAttendanceStaysInProgressUntilAnApprovedChangeClearsTheRiskAndRegularityIsRecalculated() throws Exception {
        Classroom classroom = persistClassroom("grade9", "7700011", persistScheme(2, true, null),
                Percentage.ofPoints(new BigDecimal("25")), 10, 10, InstructorRole.LEAD);
        Person preceptor = persistPerson("grade9.preceptor", "77000117", "PRECEPTOR");
        AttendanceStatus[] statuses = {AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.ABSENT, AttendanceStatus.ABSENT};
        long lastAbsentId = 0;
        for (int index = 0; index < statuses.length; index++) {
            String body = postJson(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/attendance",
                    new CargarAsistenciaRequest(LocalDate.of(2026, 3, index + 2),
                            List.of(new AttendanceEntry(classroom.enrollment().getId(), statuses[index])), null, null))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            lastAbsentId = objectMapper.readTree(body).get(0).get("id").asLong();
        }
        String riskPath = "/section-enrollments/" + classroom.enrollment().getId() + "/attendance/risk";
        getAs(preceptor, riskPath).andExpect(jsonPath("$.libre").value(true)).andExpect(jsonPath("$.absencePercent").value(40.0));

        load(classroom, GradeItemType.EXAM, 1, "7");
        load(classroom, GradeItemType.EXAM, 2, "7");
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.IN_PROGRESS);

        long requestId = bodyOf(postJson(classroom.teacher(), "/attendance/" + lastAbsentId + "/change-requests",
                new CreateAttendanceChangeRequestRequest(AttendanceStatus.PRESENT, "Error de carga"))
                .andExpect(status().isCreated())).get("id").asLong();
        postEmpty(preceptor, "/attendance/change-requests/" + requestId + "/approve").andExpect(status().isOk());

        getAs(preceptor, riskPath).andExpect(jsonPath("$.libre").value(false)).andExpect(jsonPath("$.absencePercent").value(20.0));
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.REGULAR);
    }
}
