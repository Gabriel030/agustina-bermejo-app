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
@Tag("D-376")
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

    private static List<ScheduledAllocation> schedule(CreditContributions credits, PlannedInvoiceAllocation... plan) {
        return AllocationSchedule.scheduleInvoiceAllocations(List.of(plan), credits, BUSINESS_DATE);
    }

    @Test
    void anEmptyPlanSchedulesNothing() {
        assertThat(schedule(CREDITS)).isEmpty();
    }

    @Test
    void aSingleItemTakesTheDateOfTheCreditThatCompletesItsCoverage() {
        assertThat(schedule(CREDITS, planned(1L, "80000.00"))).extracting(ScheduledAllocation::invoiceId, ScheduledAllocation::amount, ScheduledAllocation::effectiveDate)
                .containsExactly(tuple(1L, ars("80000.00"), MARCH_12));
    }

    @Test
    void eachItemConsumesCreditAfterTheOnesBeforeIt() {
        assertThat(schedule(CREDITS, planned(1L, "30000.00"), planned(2L, "20000.00"), planned(3L, "30000.00")))
                .extracting(ScheduledAllocation::invoiceId, ScheduledAllocation::amount, ScheduledAllocation::effectiveDate)
                .containsExactly(tuple(1L, ars("30000.00"), MARCH_5), tuple(2L, ars("20000.00"), MARCH_12), tuple(3L, ars("30000.00"), MARCH_12));
    }

    @Test
    void anItemEndingExactlyAtTheEndOfACreditKeepsItsDateAndOneCentMoreMovesOn() {
        assertThat(schedule(CREDITS, planned(1L, "30000.00"))).extracting(ScheduledAllocation::effectiveDate).containsExactly(MARCH_5);
        assertThat(schedule(CREDITS, planned(1L, "30000.01"))).extracting(ScheduledAllocation::effectiveDate).containsExactly(MARCH_12);
    }

    @Test
    void theEffectiveDateIsTheDateOfTheLastSourceOfEachItem() {
        List<ScheduledAllocation> schedule = schedule(CREDITS, planned(1L, "40000.00"));

        assertThat(schedule.get(0).sources()).extracting(AllocationSourceShare::creditEntryId).containsExactly(101L, 102L);
        assertThat(schedule.get(0).effectiveDate()).isEqualTo(MARCH_12);
    }

    @Test
    void creditThatCannotCoverThePlanIsAnIntegrityFailureInsteadOfAnUntraceableAllocation() {
        assertThatThrownBy(() -> schedule(new CreditContributions(List.of()), planned(1L, "1.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> schedule(CREDITS, planned(1L, "80000.01"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theScheduleKeepsTheInvoiceIdsAndAmountsOfThePlanInOrder() {
        List<ScheduledAllocation> schedule = schedule(CREDITS, planned(9L, "100.00"), planned(4L, "250.50"));

        assertThat(schedule).extracting(ScheduledAllocation::invoiceId).containsExactly(9L, 4L);
        assertThat(schedule).extracting(ScheduledAllocation::amount).containsExactly(ars("100.00"), ars("250.50"));
    }
}
