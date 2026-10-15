package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;
import com.red_escuela.backend.billing.planning.domain.CourseInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;

class CourseMonthlyInvoicePlannerTest {
    private final CourseMonthlyInvoicePlanner planner = new CourseMonthlyInvoicePlanner();
    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final Predicate<YearMonth> MARCH_TO_JUNE = m -> !m.isBefore(MARCH) && !m.isAfter(YearMonth.of(2027, 6));

    private CourseInvoicePlanningFacts facts(boolean enrolled, boolean suspended, boolean plan, PaymentPlanCadence cadence, YearMonth month,
                                    Integer duration, Set<YearMonth> issued, long inTerm, YearMonth upfrontFrom, Integer scope) {
        return new CourseInvoicePlanningFacts(1L, month, LocalDate.of(2027, 3, 4), enrolled, suspended, plan, cadence, upfrontFrom, scope,
                MARCH_TO_JUNE, duration, issued, inTerm, 10);
    }

    @Test
    @Tag("D-039")
    void monthlyChargeIsIssuedAtTheEmissionDatePriceWithDueDateTenDaysLater() {
        MonthlyInvoicePlan plan = planner.planInvoicesForMonth(facts(true, false, true, PaymentPlanCadence.MONTHLY, MARCH, 4, Set.of(), 0, null, null));

        assertThat(plan.charges()).hasSize(1);
        PlannedInvoice charge = plan.charges().get(0);
        assertThat(charge.period()).isEqualTo(MARCH);
        assertThat(charge.priceDate()).isEqualTo(LocalDate.of(2027, 3, 4));
        assertThat(charge.dueDate()).isEqualTo(LocalDate.of(2027, 3, 14));
        assertThat(charge.upfront()).isFalse();
    }

    @Test
    void notEnrolledSuspendedOrWithoutPlanNothingIsPlanned() {
        assertThat(planner.planInvoicesForMonth(facts(false, false, true, PaymentPlanCadence.MONTHLY, MARCH, 4, Set.of(), 0, null, null)).charges()).isEmpty();
        assertThat(planner.planInvoicesForMonth(facts(true, true, true, PaymentPlanCadence.MONTHLY, MARCH, 4, Set.of(), 0, null, null)).charges()).isEmpty();
        assertThat(planner.planInvoicesForMonth(facts(true, false, false, null, MARCH, 4, Set.of(), 0, null, null)).charges()).isEmpty();
    }

    @Test
    void alreadyIssuedOutsideTheTermAndCapReachedAreNotPlanned() {
        assertThat(planner.planInvoicesForMonth(facts(true, false, true, PaymentPlanCadence.MONTHLY, MARCH, 4, Set.of(MARCH), 1, null, null)).charges()).isEmpty();
        assertThat(planner.planInvoicesForMonth(facts(true, false, true, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 7), 4, Set.of(), 0, null, null)).charges()).isEmpty();
        assertThat(planner.planInvoicesForMonth(facts(true, false, true, PaymentPlanCadence.MONTHLY, MARCH, 4, Set.of(), 4, null, null)).charges()).isEmpty();
        assertThat(planner.planInvoicesForMonth(facts(true, false, true, PaymentPlanCadence.MONTHLY, MARCH, null, Set.of(), 99, null, null)).charges()).hasSize(1);
    }

    @Test
    void upfrontPlansEveryMonthOfTheScopeInsideTheTermUpToTheCap() {
        MonthlyInvoicePlan plan = planner.planInvoicesForMonth(facts(true, false, true, PaymentPlanCadence.ANNUAL_UPFRONT, MARCH, 3, Set.of(), 0, MARCH, 10));

        assertThat(plan.charges()).extracting(PlannedInvoice::period).containsExactly(MARCH, YearMonth.of(2027, 4), YearMonth.of(2027, 5));
        assertThat(plan.charges()).allMatch(PlannedInvoice::upfront);
    }

    @Test
    void upfrontSkipsWhatWasAlreadyIssued() {
        MonthlyInvoicePlan plan = planner.planInvoicesForMonth(facts(true, false, true, PaymentPlanCadence.ANNUAL_UPFRONT, MARCH, 4, Set.of(MARCH), 1, MARCH, 4));

        assertThat(plan.charges()).extracting(PlannedInvoice::period).containsExactly(YearMonth.of(2027, 4), YearMonth.of(2027, 5), YearMonth.of(2027, 6));
    }
}
