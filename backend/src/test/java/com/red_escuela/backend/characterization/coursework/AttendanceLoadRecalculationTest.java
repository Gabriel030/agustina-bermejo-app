package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
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

class AttendanceLoadRecalculationTest extends CourseworkCharacterizationSupport {

    private Classroom classroomWithMaxAbsence(String key, String dniBase) {
        return persistClassroom(key, dniBase, persistScheme(2, true, null),
                Percentage.ofPoints(new BigDecimal("25")), 10, 10, InstructorRole.LEAD);
    }

    private void loadDay(Classroom classroom, int day, AttendanceStatus attendanceStatus) throws Exception {
        postJson(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/attendance",
                new CargarAsistenciaRequest(LocalDate.of(2026, 3, day),
                        List.of(new AttendanceEntry(classroom.enrollment().getId(), attendanceStatus)), null, null))
                .andExpect(status().isCreated());
    }

    private void loadDays(Classroom classroom, int firstDay, AttendanceStatus... statuses) throws Exception {
        for (int index = 0; index < statuses.length; index++) {
            loadDay(classroom, firstDay + index, statuses[index]);
        }
    }

    private void loadExams(Classroom classroom) throws Exception {
        for (int sequence = 1; sequence <= 2; sequence++) {
            postJson(classroom.teacher(), "/section-enrollments/" + classroom.enrollment().getId() + "/grade-items",
                    new CreateGradeItemRequest(GradeItemType.EXAM, sequence, null, LocalDateTime.of(2026, 6, 1, 10, 0),
                            new BigDecimal("7"), null, null))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void loadingPresencesThatClearTheLibreConditionMakesTheStudentRegularInTheSameCall() throws Exception {
        Classroom classroom = classroomWithMaxAbsence("att1", "8000001");
        Person preceptor = persistPerson("att1.preceptor", "80000017", "PRECEPTOR");
        loadDays(classroom, 2, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT,
                AttendanceStatus.ABSENT, AttendanceStatus.ABSENT);
        loadExams(classroom);
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        String riskPath = "/section-enrollments/" + classroom.enrollment().getId() + "/attendance/risk";
        getAs(preceptor, riskPath).andExpect(jsonPath("$.libre").value(true));

        loadDays(classroom, 10, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT);

        getAs(preceptor, riskPath).andExpect(jsonPath("$.libre").value(false)).andExpect(jsonPath("$.absencePercent").value(25.0));
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void loadingAbsencesThatKeepTheStudentLibreLeavesTheEnrollmentInProgress() throws Exception {
        Classroom classroom = classroomWithMaxAbsence("att2", "8000002");
        loadDays(classroom, 2, AttendanceStatus.PRESENT, AttendanceStatus.ABSENT, AttendanceStatus.ABSENT);
        loadExams(classroom);

        loadDay(classroom, 10, AttendanceStatus.ABSENT);

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.IN_PROGRESS);
    }

    @Test
    void loadingAttendanceForAStudentWithoutAllExamsDoesNotChangeTheCondition() throws Exception {
        Classroom classroom = classroomWithMaxAbsence("att3", "8000003");

        loadDays(classroom, 2, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT);

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.IN_PROGRESS);
    }

    @Test
    void regularityIsRecalculatedPerEnrollmentWhenSeveralEntriesAreLoadedAtOnce() throws Exception {
        Classroom classroom = classroomWithMaxAbsence("att4", "8000004");
        loadDays(classroom, 2, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT,
                AttendanceStatus.ABSENT, AttendanceStatus.ABSENT);
        loadExams(classroom);
        loadDays(classroom, 10, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT);
        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.IN_PROGRESS);

        loadDay(classroom, 12, AttendanceStatus.PRESENT);

        assertThat(statusOf(classroom.enrollment())).isEqualTo(EnrollmentStatus.REGULAR);
    }
}
