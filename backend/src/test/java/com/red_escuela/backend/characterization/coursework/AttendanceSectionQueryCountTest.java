package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Percentage;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class AttendanceSectionQueryCountTest extends CourseworkCharacterizationSupport {

    private static final int MANY = 120;
    private static final int BATCH_FETCH_SIZE = 50;
    private static final int EXTRA_BATCHES = (MANY + BATCH_FETCH_SIZE - 1) / BATCH_FETCH_SIZE - 1;
    private static final LocalDate CLASS_DAY = LocalDate.of(2026, 3, 2);

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Classroom classroomWithStudents(String key, String dniBase, int students) throws Exception {
        Classroom classroom = persistClassroom(key, dniBase, persistScheme(2, true, null),
                Percentage.ofPoints(new BigDecimal("25")), 10, 10, InstructorRole.ASSISTANT);
        List<SubjectSectionEnrollment> enrollments = new ArrayList<>(List.of(classroom.enrollment()));
        for (int index = 1; index < students; index++) {
            Student student = persistStudent(persistPerson(key + ".s" + index, dniBase + "5" + index, "ALUMNO"));
            ProgramEnrollment programEnrollment = new ProgramEnrollment();
            programEnrollment.setStudent(student);
            programEnrollment.setProgram(classroom.programEnrollment().getProgram());
            programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            programEnrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(programEnrollment);
            SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgramEnrollment(programEnrollment);
            enrollment.setSubjectSection(classroom.section());
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            enrollment.setEnrolledAt(LocalDateTime.now());
            enrollment.setAttemptNumber(1);
            entityManager.persist(enrollment);
            enrollments.add(enrollment);
        }
        entityManager.flush();
        for (int day = 2; day <= 4; day++) {
            AttendanceStatus attendance = day == 2 ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
            List<AttendanceEntry> entries = enrollments.stream()
                    .map(enrollment -> new AttendanceEntry(enrollment.getId(), attendance))
                    .toList();
            postJson(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/attendance",
                    new CargarAsistenciaRequest(LocalDate.of(2026, 3, day), entries, null, null))
                    .andExpect(status().isCreated());
        }
        entityManager.flush();
        entityManager.clear();
        return classroom;
    }

    private long statementsOf(Classroom classroom, String path) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        getAs(classroom.teacher(), path).andExpect(status().isOk());
        long count = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);
        return count;
    }

    private void assertStatementsDoNotGrowWithStudents(String pathTemplate, int baseStatements, int extraStatementsPerBatch) throws Exception {
        Classroom one = classroomWithStudents("aq1", "8500001", 1);
        Classroom many = classroomWithStudents("aq2", "8500002", MANY);

        long withOne = statementsOf(one, pathTemplate.replace("{id}", one.section().getId().toString()));
        long withMany = statementsOf(many, pathTemplate.replace("{id}", many.section().getId().toString()));

        assertThat(withOne).isLessThanOrEqualTo(baseStatements);
        assertThat(withMany).isLessThanOrEqualTo(withOne + (long) EXTRA_BATCHES * extraStatementsPerBatch);
    }

    @Test
    void sectionAttendanceLoadDoesNotGrowWithStudents() throws Exception {
        assertStatementsDoNotGrowWithStudents("/program-sections/{id}/class-sessions/" + CLASS_DAY, 3, 0);
    }

    @Test
    void sectionAttendanceSummaryDoesNotGrowWithStudents() throws Exception {
        assertStatementsDoNotGrowWithStudents("/program-sections/{id}/attendance/history", 24, 2);
    }
}
