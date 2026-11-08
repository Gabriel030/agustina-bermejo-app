package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.billing.planning.domain.PaymentPlanCoverage;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.YearCycles;
import com.red_escuela.backend.billing.planning.policy.PlanningFixtures_;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-523")
@Tag("D-013")
@Tag("D-015")
class CertificateIgnoresNextYearEnrollmentFeeTest {
    private final EnrollmentFeeReferenceSlotResolver resolver = new EnrollmentFeeReferenceSlotResolver();

    private ProgramInvoicePlanningFacts factsAt(YearMonth month, YearCycles cycles) {
        return new ProgramInvoicePlanningFacts(1L, month, month.atDay(1), true, false, false, false, false,
                null, PaymentPlanCoverage.none(), true, cycles, List.of(), Set.of(), Map.of(), null, null, null);
    }

    private YearCycles twoYears() {
        ProgramYearBillingTemplate current = PlanningFixtures_.template(1L, 2026);
        ProgramYearBillingTemplate next = PlanningFixtures_.template(2L, 2027);
        return new YearCycles(List.of(TestTemplateTerms.cycleOf(current, 2026), TestTemplateTerms.cycleOf(next, 2027)), null);
    }

    @Test
    void inOctoberTheLatestDueSlotBelongsToTheCurrentYearAndNeverToTheNextOne() {
        YearCycles cycles = twoYears();

        EnrollmentFeeSlotReference reference = resolver.resolveLatestDueSlot(factsAt(YearMonth.of(2026, 10), cycles)).orElseThrow();

        assertThat(cycles.all()).extracting(cycle -> cycle.termStart().getYear()).containsExactly(2026, 2027);
        assertThat(reference.cycle().termStart().getYear()).isEqualTo(2026);
    }

    @Test
    void theNextYearCycleBecomesTheReferenceOnlyOnceItsOwnEmissionMonthArrives() {
        YearCycles cycles = twoYears();

        EnrollmentFeeSlotReference inFebruary = resolver.resolveLatestDueSlot(factsAt(YearMonth.of(2027, 2), cycles)).orElseThrow();

        assertThat(inFebruary.cycle().termStart().getYear()).isEqualTo(2027);
    }
}
