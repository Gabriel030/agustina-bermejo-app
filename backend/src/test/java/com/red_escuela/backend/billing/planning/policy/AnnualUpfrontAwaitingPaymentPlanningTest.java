package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningDecision;
import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;
import com.red_escuela.backend.billing.planning.domain.PaymentPlanCoverage;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.service.ProgramMonthlyInvoicePlanner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-160")
@Tag("D-131")
class AnnualUpfrontAwaitingPaymentPlanningTest {
    private final ProgramMonthlyInvoicePlanner planner = new ProgramMonthlyInvoicePlanner();

    private MonthlyInvoicePlan planFor(YearMonth month, Set<YearMonth> awaiting) {
        ProgramYearBillingTemplate template = PlanningFixtures.template(1, 2027, 10, 1, 3, null);
        ProgramInvoicePlanningFacts base = PlanningFixtures.facts(template, month, false, false);
        ProgramInvoicePlanningFacts facts = new ProgramInvoicePlanningFacts(base.enrollmentId(), base.month(), base.today(), base.academicallyActive(), base.suspendedOrWithdrawn(),
                base.planCompleted(), base.paused(), base.onlyFinalExamsLeft(), base.entryMonth(), PaymentPlanCoverage.ofCadence(PaymentPlanCadence.MONTHLY), true, base.cycles(),
                List.of(), Set.of(), Map.of(), null, null, null, base.today(), awaiting);
        return planner.planInvoicesForMonth(facts);
    }

    @Test
    void aMonthReservedByARequestAwaitingPaymentIsNotPlannedAndReportsItsOwnCode() {
        MonthlyInvoicePlan plan = planFor(YearMonth.of(2027, 3), Set.of(YearMonth.of(2027, 3), YearMonth.of(2027, 4)));

        assertThat(plan.has(FeeType.MONTHLY_INSTALLMENT)).isFalse();
        assertThat(plan.decisions()).extracting(InvoicePlanningDecision::code).contains("ANNUAL_UPFRONT_AWAITING_PAYMENT");
    }

    @Test
    void aMonthOutsideTheReservedOnesIsStillPlannedMonthly() {
        MonthlyInvoicePlan plan = planFor(YearMonth.of(2027, 3), Set.of(YearMonth.of(2027, 4)));

        assertThat(plan.has(FeeType.MONTHLY_INSTALLMENT)).isTrue();
        assertThat(plan.decisions()).extracting(InvoicePlanningDecision::code).doesNotContain("ANNUAL_UPFRONT_AWAITING_PAYMENT");
    }

    @Test
    void afterTheRequestIsDiscardedTheMonthIsPlannedAgain() {
        assertThat(planFor(YearMonth.of(2027, 3), Set.of(YearMonth.of(2027, 3))).has(FeeType.MONTHLY_INSTALLMENT)).isFalse();
        assertThat(planFor(YearMonth.of(2027, 3), Set.of()).has(FeeType.MONTHLY_INSTALLMENT)).isTrue();
    }

    @Test
    void aYearWithoutARequestPlansMonthlyInTheNextLectiveYear() {
        assertThat(planFor(YearMonth.of(2027, 5), Set.of(YearMonth.of(2026, 5))).has(FeeType.MONTHLY_INSTALLMENT)).isTrue();
    }
}
