package com.red_escuela.backend.billing.account.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.account.domain.OldestOverdueFirstAllocationStrategyTest.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-139")
class AllocationEffectiveDateCalculatorTest {

    private static final LocalDate FALLBACK = LocalDate.of(2027, 4, 20);
    private static final List<CreditContribution> CREDITS = List.of(
            new CreditContribution(LocalDate.of(2027, 3, 5), ars("30000.00")),
            new CreditContribution(LocalDate.of(2027, 3, 12), ars("50000.00")));

    @Test
    void theAllocationTakesTheDateOfTheCreditThatCompletedTheCoverage() {
        assertThat(AllocationEffectiveDateCalculator.calculateEffectiveDate(CREDITS, ars("0.00"), ars("80000.00"), FALLBACK))
                .isEqualTo(LocalDate.of(2027, 3, 12));
    }

    @Test
    void anAllocationCoveredByTheFirstCreditTakesItsDate() {
        assertThat(AllocationEffectiveDateCalculator.calculateEffectiveDate(CREDITS, ars("0.00"), ars("30000.00"), FALLBACK))
                .isEqualTo(LocalDate.of(2027, 3, 5));
    }

    @Test
    void creditAlreadyConsumedByEarlierAllocationsIsSkipped() {
        assertThat(AllocationEffectiveDateCalculator.calculateEffectiveDate(CREDITS, ars("30000.00"), ars("10000.00"), FALLBACK))
                .isEqualTo(LocalDate.of(2027, 3, 12));
    }

    @Test
    void oneCentBeyondTheFirstCreditMovesToTheSecond() {
        assertThat(AllocationEffectiveDateCalculator.calculateEffectiveDate(CREDITS, ars("0.00"), ars("30000.01"), FALLBACK))
                .isEqualTo(LocalDate.of(2027, 3, 12));
    }

    @Test
    void whenTheRecordedCreditsDoNotReachTheTargetTheBusinessDateIsUsed() {
        assertThat(AllocationEffectiveDateCalculator.calculateEffectiveDate(CREDITS, ars("0.00"), ars("80000.01"), FALLBACK)).isEqualTo(FALLBACK);
        assertThat(AllocationEffectiveDateCalculator.calculateEffectiveDate(List.of(), ars("0.00"), ars("1.00"), FALLBACK)).isEqualTo(FALLBACK);
    }
}
