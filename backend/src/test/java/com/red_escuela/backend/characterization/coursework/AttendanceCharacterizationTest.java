package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.dto.InstructorRole;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
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

class AttendanceCharacterizationTest extends CourseworkCharacterizationSupport {

    private static final LocalDate CLASS_DAY = LocalDate.of(2026, 3, 2);

    private CargarAsistenciaRequest attendanceOf(LocalDate day, long enrollmentId, AttendanceStatus status) {
        return new CargarAsistenciaRequest(day, List.of(new AttendanceEntry(enrollmentId, status)), "Tema 1", "Sin novedades");
    }

    private Classroom classroom(String key, String dniBase) {
        return persistClassroom(key, dniBase, persistScheme(2, true, null),
                Percentage.ofPoints(new BigDecimal("25")), 10, 10, InstructorRole.LEAD);
    }

    @Test
    void teacherLoadsAttendanceForOwnSectionAndTheClassSessionIsRecordedAsHeld() throws Exception {
        Classroom classroom = classroom("att1", "7500001");
        long sectionId = classroom.section().getId();

        postJson(classroom.teacher(), "/program-sections/" + sectionId + "/attendance",
                attendanceOf(CLASS_DAY, classroom.enrollment().getId(), AttendanceStatus.PRESENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].enrollmentId").value(classroom.enrollment().getId()))
                .andExpect(jsonPath("$[0].status").value("PRESENT"))
                .andExpect(jsonPath("$[0].classDate").value("2026-03-02"));

        assertThat(countRows("Attendance", "e.programSubjectSectionEnrollment.id = " + classroom.enrollment().getId()))
                .isEqualTo(1);
        assertThat(countRows("ClassSession", "e.classDate = {d '2026-03-02'}")).isEqualTo(1);
        getAs(classroom.teacher(), "/program-sections/" + sectionId + "/class-sessions/2026-03-02")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.topic").value("Tema 1"));
    }

    @Test
    void loadingTheSameStatusTwiceIsIdempotentAndDifferentStatusIsRejectedAsAlreadyRecorded() throws Exception {
        Classroom classroom = classroom("att2", "7500002");
        String path = "/program-sections/" + classroom.section().getId() + "/attendance";
        long enrollmentId = classroom.enrollment().getId();
        postJson(classroom.teacher(), path, attendanceOf(CLASS_DAY, enrollmentId, AttendanceStatus.ABSENT))
                .andExpect(status().isCreated());

        postJson(classroom.teacher(), path, attendanceOf(CLASS_DAY, enrollmentId, AttendanceStatus.ABSENT))
                .andExpect(status().isCreated());
        postJson(classroom.teacher(), path, attendanceOf(CLASS_DAY, enrollmentId, AttendanceStatus.PRESENT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_ALREADY_RECORDED"));

        assertThat(countRows("Attendance", "e.programSubjectSectionEnrollment.id = " + enrollmentId)).isEqualTo(1);
    }

    @Test
    void teacherCannotRecordJustifiedOrJustificationPendingStatuses() throws Exception {
        Classroom classroom = classroom("att3", "7500003");
        String path = "/program-sections/" + classroom.section().getId() + "/attendance";

        postJson(classroom.teacher(), path, attendanceOf(CLASS_DAY, classroom.enrollment().getId(), AttendanceStatus.JUSTIFIED))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_STATUS_NOT_RECORDABLE"));
        postJson(classroom.teacher(), path, attendanceOf(CLASS_DAY, classroom.enrollment().getId(), AttendanceStatus.JUSTIFICATION_PENDING))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_STATUS_NOT_RECORDABLE"));
    }

    @Test
    void teacherOfAnotherSectionCannotLoadAttendance() throws Exception {
        Classroom classroom = classroom("att4", "7500004");
        Classroom other = classroom("att4b", "7500005");

        postJson(other.teacher(), "/program-sections/" + classroom.section().getId() + "/attendance",
                attendanceOf(CLASS_DAY, classroom.enrollment().getId(), AttendanceStatus.PRESENT))
                .andExpect(status().is4xxClientError());

        assertThat(countRows("Attendance", "e.programSubjectSectionEnrollment.id = " + classroom.enrollment().getId()))
                .isZero();
    }

    @Test
    void attendanceCannotBeLoadedOnAHolidayNorByRolesOtherThanTeacher() throws Exception {
        Classroom classroom = classroom("att5", "7500006");
        Person preceptor = persistPerson("att5.preceptor", "75000067", "PRECEPTOR");
        entityManager.createNativeQuery("insert into holiday (date, description) values ('2026-05-25', 'Feriado') on conflict (date) do nothing")
                .executeUpdate();
        String path = "/program-sections/" + classroom.section().getId() + "/attendance";

        postJson(classroom.teacher(), path,
                attendanceOf(LocalDate.of(2026, 5, 25), classroom.enrollment().getId(), AttendanceStatus.PRESENT))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("CLASS_ON_HOLIDAY"));
        postJson(preceptor, path, attendanceOf(CLASS_DAY, classroom.enrollment().getId(), AttendanceStatus.PRESENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void preceptorListsEnrollmentAttendanceAndRosterIsAvailableToTheTeacher() throws Exception {
        Classroom classroom = classroom("att6", "7500007");
        Person preceptor = persistPerson("att6.preceptor", "75000077", "PRECEPTOR");
        postJson(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/attendance",
                attendanceOf(CLASS_DAY, classroom.enrollment().getId(), AttendanceStatus.LATE))
                .andExpect(status().isCreated());

        getAs(preceptor, "/section-enrollments/" + classroom.enrollment().getId() + "/attendance")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("LATE"));
        getAs(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/roster")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].enrollmentId").value(classroom.enrollment().getId()));
    }
}
