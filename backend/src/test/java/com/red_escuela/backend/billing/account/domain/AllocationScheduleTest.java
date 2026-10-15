package com.red_escuela.backend.billing.account.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@Tag("D-139")
class AllocationScheduleTest {

    private static final LocalDate MARCH_5 = LocalDate.of(2027, 3, 5);
    private static final LocalDate MARCH_12 = LocalDate.of(2027, 3, 12);
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2027, 4, 20);
    private static final CreditContributions CREDITS = new CreditContributions(List.of(
            new CreditContribution(101L, 11L, null, MARCH_5, ars("30000.00")),
            new CreditContribution(102L, 12L, null, MARCH_12, ars("50000.00"))));

    private static PlannedInvoiceAllocation planned(long invoiceId, String amount) {
        return new PlannedInvoiceAllocation(invoiceId, ars(amount));
    }

    @Test
    void anEmptyPlanSchedulesNothing() {
        assertThat(AllocationSchedule.scheduleInvoiceAllocations(List.of(), CREDITS, ars("80000.00"), BUSINESS_DATE)).isEmpty();
    }

    @Test
    void aSingleItemTakesTheDateOfTheCreditThatCompletesItsCoverage() {
        List<ScheduledAllocation> schedule = AllocationSchedule.scheduleInvoiceAllocations(List.of(planned(1L, "80000.00")), CREDITS, ars("80000.00"), BUSINESS_DATE);

        assertThat(schedule).extracting(ScheduledAllocation::invoiceId, ScheduledAllocation::amount, ScheduledAllocation::effectiveDate)
                .containsExactly(tuple(1L, ars("80000.00"), MARCH_12));
    }

    @Test
    void eachItemConsumesCreditAfterTheOnesBeforeIt() {
        List<ScheduledAllocation> schedule = AllocationSchedule.scheduleInvoiceAllocations(
                List.of(planned(1L, "30000.00"), planned(2L, "20000.00"), planned(3L, "30000.00")), CREDITS, ars("80000.00"), BUSINESS_DATE);

        assertThat(schedule).extracting(ScheduledAllocation::invoiceId, ScheduledAllocation::amount, ScheduledAllocation::effectiveDate).containsExactly(
                tuple(1L, ars("30000.00"), MARCH_5),
                tuple(2L, ars("20000.00"), MARCH_12),
                tuple(3L, ars("30000.00"), MARCH_12));
    }

    @Test
    void creditConsumedBeforeThePlanIsSkippedInTheFirstItem() {
        List<ScheduledAllocation> schedule = AllocationSchedule.scheduleInvoiceAllocations(List.of(planned(1L, "10000.00")), CREDITS, ars("50000.00"), BUSINESS_DATE);

        assertThat(schedule).extracting(ScheduledAllocation::invoiceId, ScheduledAllocation::amount, ScheduledAllocation::effectiveDate)
                .containsExactly(tuple(1L, ars("10000.00"), MARCH_12));
    }

    @Test
    void anItemEndingExactlyAtTheEndOfACreditKeepsItsDateAndOneCentMoreMovesOn() {
        assertThat(AllocationSchedule.scheduleInvoiceAllocations(List.of(planned(1L, "30000.00")), CREDITS, ars("80000.00"), BUSINESS_DATE))
                .extracting(ScheduledAllocation::effectiveDate).containsExactly(MARCH_5);
        assertThat(AllocationSchedule.scheduleInvoiceAllocations(List.of(planned(1L, "30000.01")), CREDITS, ars("80000.00"), BUSINESS_DATE))
                .extracting(ScheduledAllocation::effectiveDate).containsExactly(MARCH_12);
    }

    @Test
    void whenTheCreditsDoNotCoverTheTargetTheScheduleIsRejectedBecauseItCannotBeTraced() {
        assertThatThrownBy(() -> AllocationSchedule.scheduleInvoiceAllocations(List.of(planned(1L, "1.00")), new CreditContributions(List.of()), ars("1.00"), BUSINESS_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unallocatedCreditAboveTheCreditedTotalCountsAsNothingConsumed() {
        List<ScheduledAllocation> schedule = AllocationSchedule.scheduleInvoiceAllocations(List.of(planned(1L, "30000.00")), CREDITS, ars("90000.00"), BUSINESS_DATE);

        assertThat(schedule).extracting(ScheduledAllocation::invoiceId, ScheduledAllocation::amount, ScheduledAllocation::effectiveDate)
                .containsExactly(tuple(1L, ars("30000.00"), MARCH_5));
    }

    @Test
    void theScheduleKeepsTheInvoiceIdsAndAmountsOfThePlanInOrder() {
        List<PlannedInvoiceAllocation> plan = List.of(planned(9L, "100.00"), planned(4L, "250.50"));

        List<ScheduledAllocation> schedule = AllocationSchedule.scheduleInvoiceAllocations(plan, CREDITS, ars("350.50"), BUSINESS_DATE);

        assertThat(schedule).extracting(ScheduledAllocation::invoiceId).containsExactly(9L, 4L);
        assertThat(schedule).extracting(ScheduledAllocation::amount).containsExactly(ars("100.00"), ars("250.50"));
    }
}
