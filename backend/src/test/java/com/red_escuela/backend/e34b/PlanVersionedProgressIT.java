package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.api.CurriculumProgressView;
import com.red_escuela.backend.enrollment.api.ProgramProgressReader;
import com.red_escuela.backend.enrollment.api.ProgramProgressView;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.service.CurriculumProgressReader;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PlanVersionedProgressIT extends ReproductionFixture {

    @Autowired
    private ProgramProgressReader programProgressReader;
    @Autowired
    private CurriculumProgressReader curriculumProgressReader;

    private ProgramEnrollment enrollment;

    @BeforeEach
    void enrollmentOnPlanOneWhilePlanTwoDemandsAnExtraSubject() {
        Program program = program(4);
        Subject algebra = subject("Algebra");
        programSubject(program, algebra, 4, StatusType.ACTIVE);
        enrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        Term term = term(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
        SubjectSectionEnrollment approved = sectionEnrollment(enrollment, section(algebra, term), EnrollmentStatus.APPROVED);
        approved.setFinalScore(Grade.of(new BigDecimal("8.00")));
        approved.setFinalScoreDate(LocalDate.of(2026, 7, 1));
        entityManager.flush();

        enrollment.getPlan().expire(LocalDate.of(2026, 7, 31), null);
        entityManager.flush();
        CurriculumPlan planTwo = CurriculumPlan.draftVersionOf(program, 2, "Plan 2", LocalDate.of(2026, 8, 1), 3, 10, null);
        planTwo.activate();
        entityManager.persist(planTwo);
        ProgramSubject extra = new ProgramSubject();
        extra.setProgram(program);
        extra.setPlan(planTwo);
        extra.setSubject(subject("Geometria"));
        extra.setCredits(6);
        extra.setCurricularYear(1);
        extra.setStatus(StatusType.ACTIVE);
        entityManager.persist(extra);
        flushAndClear();
    }

    @Test
    void progressOfAnEnrollmentIgnoresSubjectsAndTotalsOfALaterPlanVersion() {
        ProgramProgressView progress = programProgressReader.calculateProgress(enrollment.getId());

        assertThat(progress.requiredCredits()).isEqualTo(4);
        assertThat(progress.pendingMandatorySubjects()).isEmpty();
        assertThat(progress.eligibleToComplete()).isTrue();
    }

    @Test
    void curriculumViewAndGraduationEngineAgreeOnTheEnrollmentPlan() {
        CurriculumProgressView.Summary summary = curriculumProgressReader.readProgress(enrollment.getId()).summary();
        ProgramProgressView progress = programProgressReader.calculateProgress(enrollment.getId());

        assertThat(summary.requiredCredits()).isEqualTo(progress.requiredCredits());
        assertThat(summary.accumulatedCredits()).isEqualTo(progress.accumulatedCredits());
        assertThat(summary.averageScore()).isEqualTo(progress.overallAverage());
        assertThat(summary.completionPercent().points()).isEqualByComparingTo("100");
        assertThat(progress.eligibleToComplete()).isTrue();
    }
}
