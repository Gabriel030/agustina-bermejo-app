package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.dto.JustificarAsistenciaRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.PublishGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EditCollisionCharacterizationTest extends CourseworkCharacterizationSupport {

    private static final LocalDate CLASS_DAY = LocalDate.of(2026, 3, 2);

    private Classroom classroom(String key, String dniBase) {
        return persistClassroom(key, dniBase, persistScheme(2, true, null),
                Percentage.ofPoints(new BigDecimal("25")), 10, 10, InstructorRole.LEAD);
    }

    private CargarAsistenciaRequest attendanceOf(long enrollmentId, AttendanceStatus status, String topic) {
        return new CargarAsistenciaRequest(CLASS_DAY, List.of(new AttendanceEntry(enrollmentId, status)), topic, null);
    }

    @Test
    void secondAttendanceTakingWithADifferentStatusForTheSameDayIsRejectedAndKeepsTheFirstOne() throws Exception {
        Classroom classroom = classroom("col1", "7900001");
        String path = "/program-sections/" + classroom.section().getId() + "/attendance";
        long enrollmentId = classroom.enrollment().getId();

        postJson(classroom.teacher(), path, attendanceOf(enrollmentId, AttendanceStatus.PRESENT, "Tema A")).andExpect(status().isCreated());
        postJson(classroom.teacher(), path, attendanceOf(enrollmentId, AttendanceStatus.ABSENT, "Tema B"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_ALREADY_RECORDED"));

        Person preceptor = persistPerson("col1.preceptor", "79000017", "PRECEPTOR");
        getAs(preceptor, "/section-enrollments/" + enrollmentId + "/attendance")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PRESENT"));
    }

    @Test
    void secondAttendanceTakingWithTheSameStatusOverwritesTheClassTopicSilently() throws Exception {
        Classroom classroom = classroom("col2", "7900002");
        String path = "/program-sections/" + classroom.section().getId() + "/attendance";
        long enrollmentId = classroom.enrollment().getId();

        postJson(classroom.teacher(), path, attendanceOf(enrollmentId, AttendanceStatus.PRESENT, "Tema A")).andExpect(status().isCreated());
        postJson(classroom.teacher(), path, attendanceOf(enrollmentId, AttendanceStatus.PRESENT, "Tema B")).andExpect(status().isCreated());

        getAs(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/class-sessions/2026-03-02")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("Tema B"));
    }

    @Test
    void secondCorrectionOfTheSameGradeItemOverwritesTheFirstSilentlyWithoutAnyVersionCheck() throws Exception {
        Classroom classroom = classroom("col3", "7900003");
        postJson(classroom.teacher(), "/section-enrollments/" + classroom.enrollment().getId() + "/grade-items",
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.of(2026, 6, 1, 10, 0),
                        new BigDecimal("5"), null, null, null)).andExpect(status().isCreated());
        postJson(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/grade-items/publish",
                new PublishGradeItemRequest(GradeItemType.EXAM, 1)).andExpect(status().isOk());
        Person directorA = persistPerson("col3.directora", "79000037", "DIRECTOR");
        Person directorB = persistPerson("col3.directorb", "79000047", "DIRECTOR");
        String itemsPath = "/section-enrollments/" + classroom.enrollment().getId() + "/grade-items";
        long gradeItemId = bodyOf(getAs(directorA, itemsPath)).get(0).get("id").asLong();
        getAs(directorB, itemsPath).andExpect(jsonPath("$[0].score").value(5.0));

        postJson(directorA, "/grade-items/" + gradeItemId + "/corrections", Map.of("newScore", 8, "reason", "Correccion de la primera persona"))
                .andExpect(status().isCreated());
        postJson(directorB, "/grade-items/" + gradeItemId + "/corrections", Map.of("newScore", 6, "reason", "Correccion de la segunda persona"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.previousScore").value(8.0))
                .andExpect(jsonPath("$.newScore").value(6.0));

        getAs(directorA, itemsPath).andExpect(jsonPath("$[0].score").value(6.0));
        getAs(directorA, "/grade-items/" + gradeItemId + "/audit").andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void secondJustificationOfTheSameAbsenceIsRejectedByTheStatusRuleAndKeepsTheFirstReason() throws Exception {
        Classroom classroom = classroom("col4", "7900004");
        Person preceptorA = persistPerson("col4.preceptora", "79000047", "PRECEPTOR");
        Person preceptorB = persistPerson("col4.preceptorb", "79000057", "PRECEPTOR");
        long attendanceId = bodyOf(postJson(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/attendance",
                attendanceOf(classroom.enrollment().getId(), AttendanceStatus.ABSENT, null)).andExpect(status().isCreated()))
                .get(0).get("id").asLong();
        String path = "/attendance/" + attendanceId + "/justify";

        postJson(preceptorA, path, new JustificarAsistenciaRequest("Certificado medico", null, "Fuera de plazo por demora del certificado"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("JUSTIFIED"));
        postJson(preceptorB, path, new JustificarAsistenciaRequest("Otro motivo distinto", null, "Fuera de plazo por otra razon"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_JUSTIFY"));
    }

    @Test
    void secondClosureObservationOnTheSameEnrollmentOverwritesTheFirstSilently() throws Exception {
        Classroom classroom = classroom("col5", "7900005");
        String sectionPath = "/program-sections/" + classroom.section().getId();
        long enrollmentId = classroom.enrollment().getId();
        postJson(classroom.teacher(), "/section-enrollments/" + enrollmentId + "/grade-items",
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.of(2026, 6, 1, 10, 0),
                        new BigDecimal("5"), null, null, null)).andExpect(status().isCreated());
        postJson(classroom.teacher(), sectionPath + "/grade-items/publish",
                new PublishGradeItemRequest(GradeItemType.EXAM, 1)).andExpect(status().isOk());
        MutableClock.INSTANCE.setDate(LocalDate.of(3000, 1, 1));
        getAs(classroom.teacher(), sectionPath + "/closure-sheet").andExpect(status().isOk());

        putJson(classroom.teacher(), sectionPath + "/closure/students/" + enrollmentId + "/observation", Map.of("observation", "Primera observacion"))
                .andExpect(status().isNoContent());
        putJson(classroom.teacher(), sectionPath + "/closure/students/" + enrollmentId + "/observation", Map.of("observation", "Segunda observacion"))
                .andExpect(status().isNoContent());

        getAs(classroom.teacher(), sectionPath + "/closure-sheet")
                .andExpect(jsonPath("$.students[0].teacherObservation").value("Segunda observacion"));
    }
}
