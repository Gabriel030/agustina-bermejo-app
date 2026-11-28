package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.service.InvoicePlanningContextLoader;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-539")
class InvoicePlanningCompletionFactsIT extends ReproductionFixture {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    @Autowired
    private InvoicePlanningContextLoader loader;

    private ProgramInvoicePlanningFacts factsOf(ProgramEnrollment enrollment) {
        flushAndClear();
        return loader.load(enrollment, YearMonth.from(TODAY), TODAY).facts();
    }

    private SubjectSectionEnrollment attempt(ProgramEnrollment enrollment, Subject subject, EnrollmentStatus status, String score) {
        Term term = term(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
        SubjectSectionEnrollment attempt = sectionEnrollment(enrollment, section(subject, term), status);
        if (score != null) {
            attempt.setFinalScore(Grade.of(new BigDecimal(score)));
            attempt.setFinalScoreDate(LocalDate.of(2026, 7, 1));
        }
        return attempt;
    }

    private void openPlanTwoWithExtraMandatorySubject(Program program, ProgramEnrollment enrollment) {
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
    }

    @Test
    void planIsCompletedWhenTheOnlyPendingSubjectWasDeactivatedInThePlan() {
        Program program = program(4);
        Subject algebra = subject("Algebra");
        programSubject(program, algebra, 4, StatusType.ACTIVE);
        programSubject(program, subject("Historia desactivada"), 2, StatusType.INACTIVE);
        ProgramEnrollment enrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        attempt(enrollment, algebra, EnrollmentStatus.APPROVED, "8.00");

        assertThat(factsOf(enrollment).planCompleted()).isTrue();
    }

    @Test
    void enrollmentOnAnOlderPlanIgnoresTheSubjectsAndCreditsOfALaterPlanVersion() {
        Program program = program(4);
        Subject algebra = subject("Algebra");
        programSubject(program, algebra, 4, StatusType.ACTIVE);
        ProgramEnrollment enrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        attempt(enrollment, algebra, EnrollmentStatus.APPROVED, "8.00");
        entityManager.flush();
        openPlanTwoWithExtraMandatorySubject(program, enrollment);

        assertThat(factsOf(enrollment).planCompleted()).isTrue();
    }

    @Test
    void planCompletionUsesTheCreditsOfThePlanAndNotTheLegacyProgramTotal() {
        Program program = program(4);
        Subject algebra = subject("Algebra");
        programSubject(program, algebra, 4, StatusType.ACTIVE);
        ProgramEnrollment enrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        attempt(enrollment, algebra, EnrollmentStatus.APPROVED, "8.00");
        entityManager.flush();
        entityManager.createQuery("update Program p set p.totalCreditsRequired = 8 where p.id = :id")
                .setParameter("id", program.getId()).executeUpdate();

        assertThat(factsOf(enrollment).planCompleted()).isTrue();
    }

    @Test
    void onlyFinalExamsLeftIgnoresDeactivatedSubjectsOfThePlan() {
        Program program = program(4);
        Subject algebra = subject("Algebra");
        programSubject(program, algebra, 4, StatusType.ACTIVE);
        programSubject(program, subject("Historia desactivada"), 2, StatusType.INACTIVE);
        ProgramEnrollment enrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        attempt(enrollment, algebra, EnrollmentStatus.REGULAR, null);

        ProgramInvoicePlanningFacts facts = factsOf(enrollment);

        assertThat(facts.onlyFinalExamsLeft()).isTrue();
        assertThat(facts.planCompleted()).isFalse();
    }

    @Test
    void enrollmentOnAnOlderPlanKeepsItsOnlyFinalExamsRegimeWhileALaterPlanAddsSubjects() {
        Program program = program(4);
        Subject algebra = subject("Algebra");
        programSubject(program, algebra, 4, StatusType.ACTIVE);
        ProgramEnrollment enrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        attempt(enrollment, algebra, EnrollmentStatus.REGULAR, null);
        entityManager.flush();
        openPlanTwoWithExtraMandatorySubject(program, enrollment);

        assertThat(factsOf(enrollment).onlyFinalExamsLeft()).isTrue();
    }
}
