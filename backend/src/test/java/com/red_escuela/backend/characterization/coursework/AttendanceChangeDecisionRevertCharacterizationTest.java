package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;
import com.red_escuela.backend.attendance.dto.RejectAttendanceChangeRequestRequest;
import com.red_escuela.backend.attendance.dto.RevertAttendanceChangeRequestDecisionRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttendanceChangeDecisionRevertCharacterizationTest extends CourseworkCharacterizationSupport {

    private static final LocalDateTime DECISION_TIME = LocalDateTime.of(2026, 11, 20, 10, 0);
    private static final String VALID_REASON = "La decisión se tomó con datos incompletos";

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    private Classroom classroom(String key, String dniBase) {
        return persistClassroom(key, dniBase, persistScheme(2, true, null),
                Percentage.ofPoints(new BigDecimal("25")), 10, 10, InstructorRole.LEAD);
    }

    private long loadAbsentDay(Classroom classroom) throws Exception {
        var request = new com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest(
                LocalDate.of(2026, 3, 2),
                List.of(new com.red_escuela.backend.attendance.dto.AttendanceEntry(classroom.enrollment().getId(), AttendanceStatus.ABSENT)),
                null, null);
        return bodyOf(postJson(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/attendance", request)
                .andExpect(status().isCreated())).get(0).get("id").asLong();
    }

    private long requestChange(Classroom classroom, long attendanceId) throws Exception {
        return bodyOf(postJson(classroom.teacher(), "/attendance/" + attendanceId + "/change-requests",
                new CreateAttendanceChangeRequestRequest(AttendanceStatus.PRESENT, "El alumno estaba presente"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long approvedRequest(Classroom classroom, Person preceptor) throws Exception {
        long attendanceId = loadAbsentDay(classroom);
        long requestId = requestChange(classroom, attendanceId);
        MutableClock.INSTANCE.setDateTime(DECISION_TIME);
        postEmpty(preceptor, "/attendance/change-requests/" + requestId + "/approve").andExpect(status().isOk());
        return requestId;
    }

    private long auditRowsFor(long requestId, String before) {
        return jdbcTemplate.queryForObject(
                "select count(*) from audit_log where action = 'ATTENDANCE_CHANGE_DECISION_REVERTED' "
                        + "and entity_type = 'ATTENDANCE_CHANGE_REQUEST' and entity_id = ? and before_value = ? "
                        + "and after_value = 'PENDING' and reason = ?",
                Long.class, requestId, before, VALID_REASON);
    }

    @Test
    void revertingAnApprovedDecisionRestoresTheAttendanceReopensTheRequestAndLeavesAnAuditRecord() throws Exception {
        Classroom classroom = classroom("rev1", "7700001");
        Person preceptor = persistPerson("rev1.preceptor", "77000017", "PRECEPTOR");
        long requestId = approvedRequest(classroom, preceptor);
        String attendancePath = "/section-enrollments/" + classroom.enrollment().getId() + "/attendance";
        getAs(preceptor, attendancePath).andExpect(jsonPath("$[0].status").value("PRESENT"));

        postJson(preceptor, "/attendance/change-requests/" + requestId + "/revert",
                new RevertAttendanceChangeRequestDecisionRequest("  " + VALID_REASON + "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        getAs(preceptor, attendancePath).andExpect(jsonPath("$[0].status").value("ABSENT"));
        getAs(preceptor, "/attendance/change-requests?status=PENDING")
                .andExpect(jsonPath("$[?(@.id==" + requestId + ")]").isNotEmpty());
        assertThat(auditRowsFor(requestId, "APPROVED")).isEqualTo(1);
    }

    @Test
    void revertingARejectedDecisionReopensTheRequestWithoutTouchingTheAttendance() throws Exception {
        Classroom classroom = classroom("rev2", "7700002");
        Person preceptor = persistPerson("rev2.preceptor", "77000027", "PRECEPTOR");
        long requestId = requestChange(classroom, loadAbsentDay(classroom));
        MutableClock.INSTANCE.setDateTime(DECISION_TIME);
        postJson(preceptor, "/attendance/change-requests/" + requestId + "/reject",
                new RejectAttendanceChangeRequestRequest("La asistencia estaba bien cargada"))
                .andExpect(status().isOk());

        postJson(preceptor, "/attendance/change-requests/" + requestId + "/revert",
                new RevertAttendanceChangeRequestDecisionRequest(VALID_REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());

        getAs(preceptor, "/section-enrollments/" + classroom.enrollment().getId() + "/attendance")
                .andExpect(jsonPath("$[0].status").value("ABSENT"));
        assertThat(auditRowsFor(requestId, "REJECTED")).isEqualTo(1);
    }

    @Test
    void revertingWithoutAReasonIsRejectedAsValidationFailed() throws Exception {
        Classroom classroom = classroom("rev3", "7700003");
        Person preceptor = persistPerson("rev3.preceptor", "77000037", "PRECEPTOR");
        long requestId = approvedRequest(classroom, preceptor);

        postEmpty(preceptor, "/attendance/change-requests/" + requestId + "/revert")
                .andExpect(status().isBadRequest());
        postJson(preceptor, "/attendance/change-requests/" + requestId + "/revert",
                new RevertAttendanceChangeRequestDecisionRequest("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        postJson(preceptor, "/attendance/change-requests/" + requestId + "/revert",
                new RevertAttendanceChangeRequestDecisionRequest("corto"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revertingExactlySevenDaysAfterTheDecisionIsAllowed() throws Exception {
        Classroom classroom = classroom("rev4", "7700004");
        Person preceptor = persistPerson("rev4.preceptor", "77000047", "PRECEPTOR");
        long requestId = approvedRequest(classroom, preceptor);
        MutableClock.INSTANCE.setDateTime(DECISION_TIME.plusDays(7));

        postJson(preceptor, "/attendance/change-requests/" + requestId + "/revert",
                new RevertAttendanceChangeRequestDecisionRequest(VALID_REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void revertingOneSecondAfterSevenDaysIsRejectedAsWindowExpired() throws Exception {
        Classroom classroom = classroom("rev5", "7700005");
        Person preceptor = persistPerson("rev5.preceptor", "77000057", "PRECEPTOR");
        long requestId = approvedRequest(classroom, preceptor);
        MutableClock.INSTANCE.setDateTime(DECISION_TIME.plusDays(7).plus(Duration.ofSeconds(1)));

        postJson(preceptor, "/attendance/change-requests/" + requestId + "/revert",
                new RevertAttendanceChangeRequestDecisionRequest(VALID_REASON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_DECISION_REVERT_WINDOW_EXPIRED"));
        getAs(preceptor, "/section-enrollments/" + classroom.enrollment().getId() + "/attendance")
                .andExpect(jsonPath("$[0].status").value("PRESENT"));
    }

    @Test
    void revertingARequestThatIsStillPendingIsRejectedAsNotDecided() throws Exception {
        Classroom classroom = classroom("rev6", "7700006");
        Person preceptor = persistPerson("rev6.preceptor", "77000067", "PRECEPTOR");
        long requestId = requestChange(classroom, loadAbsentDay(classroom));

        postJson(preceptor, "/attendance/change-requests/" + requestId + "/revert",
                new RevertAttendanceChangeRequestDecisionRequest(VALID_REASON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_CHANGE_REQUEST_NOT_DECIDED"));
    }

    @Test
    void aTeacherCannotRevertADecision() throws Exception {
        Classroom classroom = classroom("rev7", "7700007");
        Person preceptor = persistPerson("rev7.preceptor", "77000077", "PRECEPTOR");
        long requestId = approvedRequest(classroom, preceptor);

        postJson(classroom.teacher(), "/attendance/change-requests/" + requestId + "/revert",
                new RevertAttendanceChangeRequestDecisionRequest(VALID_REASON))
                .andExpect(status().isForbidden());
    }
}
