package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
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

class TeacherSectionReadQueryCountTest extends CourseworkCharacterizationSupport {

    private static final int MANY = 8;
    private static final int GRID_MAX_STATEMENTS = 9;
    private static final int ABSENCE_REPORT_MAX_STATEMENTS = 5;

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
        for (SubjectSectionEnrollment enrollment : enrollments) {
            postJson(classroom.teacher(), "/section-enrollments/" + enrollment.getId() + "/grade-items",
                    new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.of(2026, 6, 1, 10, 0),
                            new BigDecimal("7"), "c", null, null))
                    .andExpect(status().isCreated());
        }
        publishItem(classroom, GradeItemType.EXAM, 1);
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

    @Test
    void gradeGridStatementsStayUnderTheCeilingAndDoNotGrowWithTheStudents() throws Exception {
        Classroom one = classroomWithStudents("qc1", "8400001", 1);
        Classroom many = classroomWithStudents("qc2", "8400002", MANY);

        long withOne = statementsOf(one, "/program-sections/" + one.section().getId() + "/grade-grid");
        long withMany = statementsOf(many, "/program-sections/" + many.section().getId() + "/grade-grid");

        assertThat(withMany).isEqualTo(withOne);
        assertThat(withMany).isLessThanOrEqualTo(GRID_MAX_STATEMENTS);
    }

    @Test
    void absenceReportStatementsStayUnderTheCeilingAndDoNotGrowWithTheStudents() throws Exception {
        Classroom one = classroomWithStudents("qc3", "8400003", 1);
        Classroom many = classroomWithStudents("qc4", "8400004", MANY);

        long withOne = statementsOf(one, "/program-sections/" + one.section().getId() + "/attendance/absence-report");
        long withMany = statementsOf(many, "/program-sections/" + many.section().getId() + "/attendance/absence-report");

        assertThat(withMany).isEqualTo(withOne);
        assertThat(withMany).isLessThanOrEqualTo(ABSENCE_REPORT_MAX_STATEMENTS);
    }
}
