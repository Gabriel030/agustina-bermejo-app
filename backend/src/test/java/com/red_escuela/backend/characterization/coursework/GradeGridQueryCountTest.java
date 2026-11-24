package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.TeacherGradeGridResponse;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.usecase.gradegrid.GetSectionGradeGridCommand;
import com.red_escuela.backend.evaluation.usecase.gradegrid.GetStaffProgramSectionGradeGridUseCase;
import com.red_escuela.backend.evaluation.usecase.gradegrid.GetTeacherProgramSectionGradeGridUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Grade;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class GradeGridQueryCountTest extends CourseworkCharacterizationSupport {

    private static final int MAX_TEACHER_QUERIES = 7;
    private static final int MAX_STAFF_QUERIES = 6;
    private static final int FEW_STUDENTS = 3;
    private static final int MANY_STUDENTS = 25;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private GetTeacherProgramSectionGradeGridUseCase teacherGridUseCase;

    @Autowired
    private GetStaffProgramSectionGradeGridUseCase staffGridUseCase;

    @Test
    void teacherGridQueryCountDoesNotGrowWithTheNumberOfStudents() {
        long few = teacherQueries("qcTeacherFew", "8300", FEW_STUDENTS);
        long many = teacherQueries("qcTeacherMany", "8400", MANY_STUDENTS);

        assertThat(many).isEqualTo(few).isLessThanOrEqualTo(MAX_TEACHER_QUERIES);
    }

    @Test
    void staffGridQueryCountDoesNotGrowWithTheNumberOfStudents() {
        long few = staffQueries("qcStaffFew", "8500", FEW_STUDENTS);
        long many = staffQueries("qcStaffMany", "8600", MANY_STUDENTS);

        assertThat(many).isEqualTo(few).isLessThanOrEqualTo(MAX_STAFF_QUERIES);
    }

    private long teacherQueries(String key, String dniBase, int students) {
        Classroom classroom = classroomWithStudents(key, dniBase, students);
        return countStatements(() -> teacherGridUseCase.get(
                new GetSectionGradeGridCommand(classroom.section().getId(), classroom.instructor().getId())), students);
    }

    private long staffQueries(String key, String dniBase, int students) {
        Classroom classroom = classroomWithStudents(key, dniBase, students);
        return countStatements(() -> staffGridUseCase.get(classroom.section().getId()), students);
    }

    private long countStatements(Supplier<TeacherGradeGridResponse> action, int expectedStudents) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        TeacherGradeGridResponse response = action.get();
        long statements = statistics.getPrepareStatementCount();
        assertThat(response.students()).hasSize(expectedStudents);
        assertThat(response.students()).allSatisfy(row -> assertThat(row.grades()).hasSize(2));
        return statements;
    }

    private Classroom classroomWithStudents(String key, String dniBase, int students) {
        Classroom classroom = persistClassroom(key, dniBase, persistScheme(2, false, null), null, 10, 10,
                InstructorRole.ASSISTANT);
        persistGrade(classroom.enrollment(), 1, "7");
        persistGrade(classroom.enrollment(), 2, "8");
        for (int index = 1; index < students; index++) {
            Person person = persistPerson(key + ".extra" + index, dniBase + "9" + String.format("%03d", index), "ALUMNO");
            Student student = persistStudent(person);
            SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgramEnrollment(classroom.programEnrollment());
            enrollment.setSubjectSection(classroom.section());
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            enrollment.setEnrolledAt(LocalDateTime.now());
            enrollment.setAttemptNumber(1);
            entityManager.persist(enrollment);
            persistGrade(enrollment, 1, "6");
            persistGrade(enrollment, 2, "9");
        }
        entityManager.flush();
        entityManager.clear();
        return classroom;
    }

    private void persistGrade(SubjectSectionEnrollment enrollment, int sequence, String score) {
        GradeItem item = new GradeItem();
        item.setProgramSubjectSectionEnrollment(enrollment);
        item.setType(GradeItemType.EXAM);
        item.setSequenceNumber(sequence);
        item.setDate(LocalDateTime.of(2026, 6, 1, 10, 0));
        item.setScore(Grade.of(new BigDecimal(score)));
        entityManager.persist(item);
    }
}
