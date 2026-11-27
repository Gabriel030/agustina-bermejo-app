package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.service.CycleApproval;
import com.red_escuela.backend.enrollment.api.ProgramProgressReader;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.service.SubjectEquivalenceEligibilityGuard;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanAndCycleRulesReproductionIT extends ReproductionFixture {

    @Autowired
    private ProgramProgressReader programProgressReader;
    @Autowired
    private CycleApproval cycleApproval;
    @Autowired
    private SubjectEquivalenceEligibilityGuard equivalenceEligibilityGuard;

    @Test
    void changingPlanCreditsRetroactivelyChangesTheProgressOfACompletedGraduate() {
        Program program = program(4);
        Subject subject = subject("Algebra");
        ProgramSubject planEntry = programSubject(program, subject, 4, StatusType.ACTIVE);
        ProgramEnrollment enrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.COMPLETED);
        Term term = term(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
        SubjectSectionEnrollment approved = sectionEnrollment(enrollment, section(subject, term), EnrollmentStatus.APPROVED);
        approved.setFinalScore(Grade.of(new BigDecimal("8.00")));
        flushAndClear();

        var before = programProgressReader.calculateProgress(enrollment.getId());
        assertThat(before.eligibleToComplete()).isTrue();

        entityManager.createQuery("update Program p set p.totalCreditsRequired = 8 where p.id = :id")
                .setParameter("id", program.getId()).executeUpdate();
        entityManager.createQuery("update ProgramSubject ps set ps.credits = 2 where ps.id = :id")
                .setParameter("id", planEntry.getId()).executeUpdate();
        flushAndClear();

        var after = programProgressReader.calculateProgress(enrollment.getId());
        assertThat(after.requiredCredits()).isEqualTo(8);
        assertThat(after.accumulatedCredits()).isEqualTo(2);
        assertThat(after.eligibleToComplete()).isFalse();
    }

    @Test
    @Disabled("E34-B-P3")
    void cycleOfAnAlreadyEndedTermCannotBeApproved() {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Program program = fixtures.program();
        Term pastTerm = fixtures.term(2020);
        pastTerm.setStartDate(LocalDateTime.of(2020, 3, 1, 0, 0));
        pastTerm.setEndDate(LocalDateTime.of(2020, 7, 1, 0, 0));
        AcademicOfferingCycle cycle = fixtures.programCycle(program, pastTerm, AcademicOfferingCycleStatus.PENDING_APPROVAL);
        com.red_escuela.backend.academicstructure.RequesterAuthentication.authenticateNewRequester(entityManager);
        flushAndClear();

        try {
            assertThatThrownBy(() -> cycleApproval.approve(cycle.getId(), "reproduction-key")).isInstanceOf(RuntimeException.class);
        } finally {
            com.red_escuela.backend.academicstructure.RequesterAuthentication.clear();
        }
    }

    @Test
    @Disabled("E34-B-P3")
    void courseWaitlistWithoutDeadlineOffsetStillExpiresAfterTheTermEnds() {
        Term term = new Term();
        term.setStartDate(LocalDateTime.of(2027, 3, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(2027, 7, 1, 0, 0));
        Course course = new Course();
        course.setTerm(term);
        course.setWaitlistDeadlineOffsetDays(null);

        assertThat(course.waitlistDeadlineHasPassed(LocalDate.of(2027, 7, 2))).isTrue();
    }

    @Test
    @Disabled("E34-B-P2")
    void equivalenceCannotBeRecordedForASubjectTheStudentIsRegularIn() {
        Program program = program(4);
        Subject subject = subject("Algebra");
        programSubject(program, subject, 4, StatusType.ACTIVE);
        ProgramEnrollment enrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        Term term = term(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
        sectionEnrollment(enrollment, section(subject, term), EnrollmentStatus.REGULAR);
        flushAndClear();

        assertThatThrownBy(() -> equivalenceEligibilityGuard.requireSubjectOpenForEquivalence(enrollment.getId(), subject.getId()))
                .isInstanceOf(RuntimeException.class);
    }
}
