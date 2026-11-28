package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.shared.domain.Grade;
import java.math.BigDecimal;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrityConstraintsIT extends ReproductionFixture {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SubjectSection persistedSection() {
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
        SubjectSection section = section(subject, term);
        flushAndClear();
        return section;
    }

    @Test
    void zeroCapacityOnSubjectSectionIsRejectedBySql() {
        SubjectSection section = persistedSection();

        assertThatThrownBy(() -> jdbcTemplate.update("update subject_section set capacity = 0 where id = ?", section.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void zeroCapacityOnClassroomIsRejectedBySql() {
        assertThatThrownBy(() -> jdbcTemplate.update("insert into classroom (name, capacity) values ('Aula cero', 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void zeroCapacityOnCourseIsRejectedBySql() {
        assertThatThrownBy(() -> jdbcTemplate.update("insert into course (name, capacity) values ('Curso cero', 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void zeroAttemptNumberOnSubjectSectionEnrollmentIsRejectedBySql() {
        Program program = program(4);
        SubjectSection section = persistedSection();
        ProgramEnrollment programEnrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment enrollment = sectionEnrollment(programEnrollment, entityManager.find(SubjectSection.class, section.getId()),
                EnrollmentStatus.IN_PROGRESS);
        flushAndClear();

        assertThatThrownBy(() -> jdbcTemplate.update("update subject_section_enrollment set attempt_number = 0 where id = ?",
                enrollment.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentTermEditIsDetectedByVersion() {
        Term term = term(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
        flushAndClear();
        Term stale = entityManager.find(Term.class, term.getId());
        jdbcTemplate.update("update term set version = version + 1 where id = ?", term.getId());

        stale.setDescription("Edicion concurrente");

        assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(OptimisticLockException.class);
    }

    @Test
    void concurrentProgramEditIsDetectedByVersion() {
        Program program = program(4);
        flushAndClear();
        Program stale = entityManager.find(Program.class, program.getId());
        jdbcTemplate.update("update program set version = version + 1 where id = ?", program.getId());

        stale.setName("Edicion concurrente");

        assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(OptimisticLockException.class);
    }

    @Test
    void concurrentEvaluationSchemeEditIsDetectedByVersion() {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Esquema");
        scheme.setExamCountExpected(2);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(true);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6)));
        scheme.setPromotionMinGrade(Grade.of(BigDecimal.valueOf(7)));
        entityManager.persist(scheme);
        flushAndClear();
        EvaluationScheme stale = entityManager.find(EvaluationScheme.class, scheme.getId());
        jdbcTemplate.update("update evaluation_scheme set version = version + 1 where id = ?", scheme.getId());

        stale.setName("Edicion concurrente");

        assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(OptimisticLockException.class);
    }
}
