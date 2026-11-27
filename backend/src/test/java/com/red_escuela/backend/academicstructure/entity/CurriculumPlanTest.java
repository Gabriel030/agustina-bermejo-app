package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.academicstructure.exception.CurriculumPlanNotOpenForNewStudentsException;
import com.red_escuela.backend.academicstructure.exception.InvalidCurriculumPlanDatesException;
import com.red_escuela.backend.academicstructure.exception.InvalidCurriculumPlanTransitionException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurriculumPlanTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 1);

    private CurriculumPlan planWithStatus(CurriculumPlanStatus status) {
        return CurriculumPlan.builder().id(7L).program(Program.builder().id(1L).name("Enfermería").build())
                .versionNumber(1).name("Plan v1").status(status).validFrom(LocalDate.of(2020, 3, 1)).build();
    }

    @Test
    void draftPlanCannotBeExpired() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.DRAFT);

        assertThatThrownBy(() -> plan.expire(TODAY, null)).isInstanceOf(InvalidCurriculumPlanTransitionException.class);
    }

    @Test
    void expiredPlanCannotBeActivatedAgain() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.EXPIRED);

        assertThatThrownBy(plan::activate).isInstanceOf(InvalidCurriculumPlanTransitionException.class);
    }

    @Test
    void activatingDraftMakesItActive() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.DRAFT);

        plan.activate();

        assertThat(plan.getStatus()).isEqualTo(CurriculumPlanStatus.ACTIVE);
    }

    @Test
    void expiringRecordsValidityEndAndGraduationDeadline() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.ACTIVE);

        plan.expire(TODAY, TODAY.plusYears(2));

        assertThat(plan.getStatus()).isEqualTo(CurriculumPlanStatus.EXPIRED);
        assertThat(plan.getValidUntil()).isEqualTo(TODAY);
        assertThat(plan.getGraduationDeadline()).isEqualTo(TODAY.plusYears(2));
    }

    @Test
    void graduationDeadlineBeforeExpirationIsRejected() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.ACTIVE);

        assertThatThrownBy(() -> plan.expire(TODAY, TODAY.minusDays(1))).isInstanceOf(InvalidCurriculumPlanDatesException.class);
        assertThat(plan.getStatus()).isEqualTo(CurriculumPlanStatus.ACTIVE);
    }

    @Test
    void graduationDeadlineEqualToExpirationIsAccepted() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.ACTIVE);

        assertThatCode(() -> plan.expire(TODAY, TODAY)).doesNotThrowAnyException();
    }

    @Test
    void expirationBeforeValidityStartIsRejected() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.ACTIVE);

        assertThatThrownBy(() -> plan.expire(LocalDate.of(2020, 2, 28), null)).isInstanceOf(InvalidCurriculumPlanDatesException.class);
    }

    @Test
    void expiredPlanRejectsNewStudents() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.ACTIVE);
        plan.expire(TODAY, null);

        assertThatThrownBy(() -> plan.requireOpenForNewStudents(TODAY)).isInstanceOf(CurriculumPlanNotOpenForNewStudentsException.class);
    }

    @Test
    void draftPlanRejectsNewStudents() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.DRAFT);

        assertThatThrownBy(() -> plan.requireOpenForNewStudents(TODAY)).isInstanceOf(CurriculumPlanNotOpenForNewStudentsException.class);
    }

    @Test
    void activePlanAdmitsNewStudentsFromItsFirstDay() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.ACTIVE);

        assertThatCode(() -> plan.requireOpenForNewStudents(plan.getValidFrom())).doesNotThrowAnyException();
        assertThatThrownBy(() -> plan.requireOpenForNewStudents(plan.getValidFrom().minusDays(1)))
                .isInstanceOf(CurriculumPlanNotOpenForNewStudentsException.class);
    }

    @Test
    void activePlanPastItsValidityEndRejectsNewStudents() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.ACTIVE);
        plan.setValidUntil(TODAY);

        assertThatCode(() -> plan.requireOpenForNewStudents(TODAY)).doesNotThrowAnyException();
        assertThatThrownBy(() -> plan.requireOpenForNewStudents(TODAY.plusDays(1)))
                .isInstanceOf(CurriculumPlanNotOpenForNewStudentsException.class);
    }

    @Test
    void expiredPlanWithoutDeadlineLetsExistingStudentsContinueIndefinitely() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.ACTIVE);
        plan.expire(TODAY, null);

        assertThat(plan.allowsExistingStudentsToContinue(TODAY.plusYears(30))).isTrue();
    }

    @Test
    void expiredPlanLetsExistingStudentsContinueUntilTheDeadlineInclusive() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.ACTIVE);
        LocalDate deadline = TODAY.plusYears(2);
        plan.expire(TODAY, deadline);

        assertThat(plan.allowsExistingStudentsToContinue(deadline)).isTrue();
        assertThat(plan.allowsExistingStudentsToContinue(deadline.plusDays(1))).isFalse();
    }

    @Test
    void reschedulingKeepsFieldsLeftOutOfTheRequest() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.ACTIVE);
        plan.setGraduationDeadline(TODAY);

        plan.reschedule(null, null);

        assertThat(plan.getValidFrom()).isEqualTo(LocalDate.of(2020, 3, 1));
        assertThat(plan.getGraduationDeadline()).isEqualTo(TODAY);
    }

    @Test
    void changingTotalsKeepsFieldsLeftOutOfTheRequest() {
        CurriculumPlan plan = planWithStatus(CurriculumPlanStatus.DRAFT);
        plan.setTotalYears(3);
        plan.setTotalCreditsRequired(100);

        plan.changeTotals(null, 120, 900);

        assertThat(plan.getTotalYears()).isEqualTo(3);
        assertThat(plan.getTotalCreditsRequired()).isEqualTo(120);
        assertThat(plan.getTotalHoursRequired()).isEqualTo(900);
    }
}
