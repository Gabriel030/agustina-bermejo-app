package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.testsupport.AsyncNotifications;
import org.springframework.test.context.transaction.TestTransaction;
import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttendanceChangeRequestAndRiskCharacterizationTest extends CourseworkCharacterizationSupport {

    private Classroom classroomWithMaxAbsence(String key, String dniBase, Percentage maxAbsence) {
        return persistClassroom(key, dniBase, persistScheme(2, true, null), maxAbsence, 10, 10, InstructorRole.LEAD);
    }

    private JsonNode loadDay(Classroom classroom, int day, AttendanceStatus status) throws Exception {
        CargarAsistenciaRequest request = new CargarAsistenciaRequest(
                LocalDate.of(2026, 3, day), List.of(new AttendanceEntry(classroom.enrollment().getId(), status)), null, null);
        return bodyOf(postJson(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/attendance", request)
                .andExpect(status().isCreated())).get(0);
    }

    private void loadFourDays(Classroom classroom, AttendanceStatus... statuses) throws Exception {
        for (int index = 0; index < statuses.length; index++) {
            loadDay(classroom, index + 2, statuses[index]);
        }
    }

    @Test
    void riskReportsLibreWhenAbsencePercentageExceedsTheConfiguredMaximum() throws Exception {
        Classroom classroom = classroomWithMaxAbsence("risk1", "7600001", Percentage.ofPoints(new BigDecimal("25")));
        Person preceptor = persistPerson("risk1.preceptor", "76000017", "PRECEPTOR");
        loadFourDays(classroom, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.ABSENT, AttendanceStatus.ABSENT);

        getAs(preceptor, "/section-enrollments/" + classroom.enrollment().getId() + "/attendance/risk")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculable").value(true))
                .andExpect(jsonPath("$.libre").value(true))
                .andExpect(jsonPath("$.absencePercent").value(50.0))
                .andExpect(jsonPath("$.maxAbsencePercent").value(25.0));
    }

    @Test
    void riskIsNotLibreWhenAbsencesEqualTheMaximumAndLateCountsAsPresence() throws Exception {
        Classroom classroom = classroomWithMaxAbsence("risk2", "7600002", Percentage.ofPoints(new BigDecimal("25")));
        Person preceptor = persistPerson("risk2.preceptor", "76000027", "PRECEPTOR");
        loadFourDays(classroom, AttendanceStatus.PRESENT, AttendanceStatus.LATE, AttendanceStatus.LATE, AttendanceStatus.ABSENT);

        getAs(preceptor, "/section-enrollments/" + classroom.enrollment().getId() + "/attendance/risk")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libre").value(false))
                .andExpect(jsonPath("$.absencePercent").value(25.0));
    }

    @Test
    void riskWithoutClassesIsCalculableButNotLibreAndWithoutConfiguredMaximumIsNotCalculable() throws Exception {
        Classroom withMax = classroomWithMaxAbsence("risk3", "7600003", Percentage.ofPoints(new BigDecimal("25")));
        Classroom withoutMax = classroomWithMaxAbsence("risk3b", "7600004", null);
        Person preceptor = persistPerson("risk3.preceptor", "76000037", "PRECEPTOR");

        getAs(preceptor, "/section-enrollments/" + withMax.enrollment().getId() + "/attendance/risk")
                .andExpect(jsonPath("$.calculable").value(true))
                .andExpect(jsonPath("$.libre").value(false))
                .andExpect(jsonPath("$.absencePercent").doesNotExist());
        getAs(preceptor, "/section-enrollments/" + withoutMax.enrollment().getId() + "/attendance/risk")
                .andExpect(jsonPath("$.calculable").value(false))
                .andExpect(jsonPath("$.libre").value(false));
    }

    @Test
    void approvedChangeRequestUpdatesTheAttendanceRecalculatesRiskAndNotifiesTheTeacher() throws Exception {
        Classroom classroom = classroomWithMaxAbsence("chg1", "7600005", Percentage.ofPoints(new BigDecimal("25")));
        Person preceptor = persistPerson("chg1.preceptor", "76000057", "PRECEPTOR");
        loadFourDays(classroom, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.ABSENT, AttendanceStatus.ABSENT);
        long absentAttendanceId = loadDay(classroom, 5, AttendanceStatus.ABSENT).get("id").asLong();
        String riskPath = "/section-enrollments/" + classroom.enrollment().getId() + "/attendance/risk";
        getAs(preceptor, riskPath).andExpect(jsonPath("$.libre").value(true)).andExpect(jsonPath("$.absencePercent").value(50.0));

        long requestId = bodyOf(postJson(classroom.teacher(), "/attendance/" + absentAttendanceId + "/change-requests",
                new CreateAttendanceChangeRequestRequest(AttendanceStatus.PRESENT, "El alumno llego tarde pero estaba"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.previousStatus").value("ABSENT"))
                .andExpect(jsonPath("$.requestedStatus").value("PRESENT"))).get("id").asLong();

        getAs(preceptor, "/attendance/change-requests?status=PENDING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + requestId + ")]").isNotEmpty());
        getAs(preceptor, riskPath).andExpect(jsonPath("$.absencePercent").value(50.0));

        postEmpty(preceptor, "/attendance/change-requests/" + requestId + "/approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        getAs(preceptor, "/section-enrollments/" + classroom.enrollment().getId() + "/attendance")
                .andExpect(jsonPath("$[?(@.id==" + absentAttendanceId + ")].status").value("PRESENT"));
        getAs(preceptor, riskPath)
                .andExpect(jsonPath("$.absencePercent").value(25.0))
                .andExpect(jsonPath("$.libre").value(false));
        getAs(classroom.teacher(), "/me/notifications")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='ATTENDANCE_CHANGE_REQUEST_APPROVED')]").isNotEmpty());
    }

    @Test
    void approvingTheChangeThatBringsAbsencesUnderTheMaximumClearsTheLibreCondition() throws Exception {
        Classroom classroom = classroomWithMaxAbsence("chg2", "7600006", Percentage.ofPoints(new BigDecimal("30")));
        Person preceptor = persistPerson("chg2.preceptor", "76000067", "PRECEPTOR");
        loadFourDays(classroom, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT);
        long absentAttendanceId = loadDay(classroom, 5, AttendanceStatus.ABSENT).get("id").asLong();
        String riskPath = "/section-enrollments/" + classroom.enrollment().getId() + "/attendance/risk";
        getAs(preceptor, riskPath).andExpect(jsonPath("$.libre").value(false)).andExpect(jsonPath("$.absencePercent").value(25.0));
        long requestId = bodyOf(postJson(classroom.teacher(), "/attendance/" + absentAttendanceId + "/change-requests",
                new CreateAttendanceChangeRequestRequest(AttendanceStatus.PRESENT, "Error de carga"))
                .andExpect(status().isCreated())).get("id").asLong();

        postEmpty(preceptor, "/attendance/change-requests/" + requestId + "/approve").andExpect(status().isOk());

        getAs(preceptor, riskPath).andExpect(jsonPath("$.absencePercent").value(0.0)).andExpect(jsonPath("$.libre").value(false));
    }

    @Test
    void rejectedChangeRequestLeavesTheAttendanceUntouchedAndNotifiesTheTeacher() throws Exception {
        Classroom classroom = classroomWithMaxAbsence("chg3", "7600007", Percentage.ofPoints(new BigDecimal("25")));
        Person preceptor = persistPerson("chg3.preceptor", "76000077", "PRECEPTOR");
        long attendanceId = loadDay(classroom, 2, AttendanceStatus.ABSENT).get("id").asLong();
        long requestId = bodyOf(postJson(classroom.teacher(), "/attendance/" + attendanceId + "/change-requests",
                new CreateAttendanceChangeRequestRequest(AttendanceStatus.PRESENT, "Reclamo"))
                .andExpect(status().isCreated())).get("id").asLong();

        postEmpty(preceptor, "/attendance/change-requests/" + requestId + "/reject")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        AsyncNotifications.commitTestTransaction();
        TestTransaction.start();
        postEmpty(preceptor, "/attendance/change-requests/" + requestId + "/approve")
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("INVALID_ATTENDANCE_CHANGE_REQUEST_STATE"));
        TestTransaction.end();
        TestTransaction.start();

        getAs(preceptor, "/section-enrollments/" + classroom.enrollment().getId() + "/attendance")
                .andExpect(jsonPath("$[0].status").value("ABSENT"));
        getAs(classroom.teacher(), "/me/notifications")
                .andExpect(jsonPath("$[?(@.type=='ATTENDANCE_CHANGE_REQUEST_REJECTED')]").isNotEmpty());
    }

    @Test
    void changeRequestsAreRestrictedToOwnSectionTeachersAndRecordableStatuses() throws Exception {
        Classroom classroom = classroomWithMaxAbsence("chg4", "7600008", Percentage.ofPoints(new BigDecimal("25")));
        Classroom other = classroomWithMaxAbsence("chg4b", "7600009", Percentage.ofPoints(new BigDecimal("25")));
        long attendanceId = loadDay(classroom, 2, AttendanceStatus.ABSENT).get("id").asLong();

        postJson(other.teacher(), "/attendance/" + attendanceId + "/change-requests",
                new CreateAttendanceChangeRequestRequest(AttendanceStatus.PRESENT, "No es mi comision"))
                .andExpect(status().is4xxClientError());
        postJson(classroom.teacher(), "/attendance/" + attendanceId + "/change-requests",
                new CreateAttendanceChangeRequestRequest(AttendanceStatus.JUSTIFIED, "No permitido"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_STATUS_NOT_RECORDABLE"));
        assertThat(countRows("AttendanceChangeRequest", "e.attendance.id = " + attendanceId)).isZero();
    }

    @Test
    void settingTheSectionMaximumAbsenceMakesTheRiskCalculableAndLibreFollowsTheNewLimit() throws Exception {
        Classroom classroom = classroomWithMaxAbsence("setmax1", "7600010", null);
        Person preceptor = persistPerson("setmax1.preceptor", "76000107", "PRECEPTOR");
        loadFourDays(classroom, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.ABSENT, AttendanceStatus.ABSENT);
        String riskPath = "/section-enrollments/" + classroom.enrollment().getId() + "/attendance/risk";
        String sectionPath = "/program-sections/" + classroom.section().getId();
        getAs(preceptor, riskPath).andExpect(jsonPath("$.calculable").value(false)).andExpect(jsonPath("$.libre").value(false));

        putJson(preceptor, sectionPath, java.util.Map.of("maxAbsencePercent", 25))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxAbsencePercent").value(25));
        getAs(preceptor, riskPath)
                .andExpect(jsonPath("$.calculable").value(true))
                .andExpect(jsonPath("$.libre").value(true))
                .andExpect(jsonPath("$.maxAbsencePercent").value(25.0));

        putJson(preceptor, sectionPath, java.util.Map.of("maxAbsencePercent", 60))
                .andExpect(status().isOk());
        getAs(preceptor, riskPath).andExpect(jsonPath("$.libre").value(false));
        getAs(preceptor, sectionPath).andExpect(jsonPath("$.maxAbsencePercent").value(60));
    }

    @Test
    void updatingASectionWithoutTheMaximumKeepsTheConfiguredOne() throws Exception {
        Classroom classroom = classroomWithMaxAbsence("setmax2", "7600011", Percentage.ofPoints(new BigDecimal("30")));
        Person preceptor = persistPerson("setmax2.preceptor", "76000117", "PRECEPTOR");

        putJson(preceptor, "/program-sections/" + classroom.section().getId(), java.util.Map.of("capacity", 12))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxAbsencePercent").value(30));
    }

    @Test
    void settingTheMaximumAbsenceRejectsOutOfRangeValuesAndRolesWithoutPermission() throws Exception {
        Classroom classroom = classroomWithMaxAbsence("setmax3", "7600012", null);
        Person preceptor = persistPerson("setmax3.preceptor", "76000127", "PRECEPTOR");
        String sectionPath = "/program-sections/" + classroom.section().getId();

        putJson(preceptor, sectionPath, java.util.Map.of("maxAbsencePercent", 101)).andExpect(status().isBadRequest());
        putJson(preceptor, sectionPath, java.util.Map.of("maxAbsencePercent", -1)).andExpect(status().isBadRequest());
        putJson(classroom.teacher(), sectionPath, java.util.Map.of("maxAbsencePercent", 25)).andExpect(status().isForbidden());
    }
}
