package com.red_escuela.backend.billing.account.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@Tag("D-376")
@Tag("D-139")
class AllocationSourcesTest {

    private static final LocalDate MARCH_5 = LocalDate.of(2027, 3, 5);
    private static final LocalDate MARCH_12 = LocalDate.of(2027, 3, 12);
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2027, 4, 20);

    private static CreditContribution payment(long entryId, long paymentId, LocalDate date, String amount) {
        return new CreditContribution(entryId, paymentId, null, date, ars(amount));
    }

    private static CreditContribution creditNote(long entryId, long creditNoteId, LocalDate date, String amount) {
        return new CreditContribution(entryId, null, creditNoteId, date, ars(amount));
    }

    private static List<ScheduledAllocation> schedule(CreditContributions credits, String unallocated, PlannedInvoiceAllocation... plan) {
        return AllocationSchedule.scheduleInvoiceAllocations(List.of(plan), credits, ars(unallocated), BUSINESS_DATE);
    }

    @Test
    void twoPaymentsThatFinanceOneInvoiceAreItsTwoSources() {
        CreditContributions credits = new CreditContributions(List.of(payment(1L, 11L, MARCH_5, "3000.00"), payment(2L, 12L, MARCH_12, "5000.00")));

        List<ScheduledAllocation> schedule = schedule(credits, "8000.00", new PlannedInvoiceAllocation(7L, ars("8000.00")));

        assertThat(schedule).hasSize(1);
        assertThat(schedule.get(0).sources()).extracting(AllocationSourceShare::paymentId, share -> share.amount().value().amount().toPlainString())
                .containsExactly(tuple(11L, "3000.00"), tuple(12L, "5000.00"));
        assertThat(schedule.get(0).effectiveDate()).isEqualTo(MARCH_12);
    }

    @Test
    void onePaymentThatFinancesTwoInvoicesIsASourceOfBothAndNeverExceedsItself() {
        CreditContributions credits = new CreditContributions(List.of(payment(1L, 11L, MARCH_5, "10000.00")));

        List<ScheduledAllocation> schedule = schedule(credits, "10000.00",
                new PlannedInvoiceAllocation(7L, ars("6000.00")), new PlannedInvoiceAllocation(8L, ars("4000.00")));

        assertThat(schedule).extracting(ScheduledAllocation::invoiceId, allocation -> allocation.sources().get(0).paymentId(),
                allocation -> allocation.sources().get(0).amount().value().amount().toPlainString())
                .containsExactly(tuple(7L, 11L, "6000.00"), tuple(8L, 11L, "4000.00"));
    }

    @Test
    void aCreditNoteAndAPaymentThatFinanceTheSameInvoiceAreBothSources() {
        CreditContributions credits = new CreditContributions(List.of(creditNote(1L, 21L, MARCH_5, "2000.00"), payment(2L, 12L, MARCH_12, "3000.00")));

        List<ScheduledAllocation> schedule = schedule(credits, "5000.00", new PlannedInvoiceAllocation(7L, ars("5000.00")));

        assertThat(schedule.get(0).sources()).extracting(AllocationSourceShare::creditNoteId, AllocationSourceShare::paymentId)
                .containsExactly(tuple(21L, null), tuple(null, 12L));
    }

    @Test
    void creditAlreadyConsumedBeforeThePlanIsSkippedInsideAnEntry() {
        CreditContributions credits = new CreditContributions(List.of(payment(1L, 11L, MARCH_5, "3000.00"), payment(2L, 12L, MARCH_12, "5000.00")));

        List<ScheduledAllocation> schedule = schedule(credits, "6500.00", new PlannedInvoiceAllocation(7L, ars("6500.00")));

        assertThat(schedule.get(0).sources()).extracting(AllocationSourceShare::paymentId, share -> share.amount().value().amount().toPlainString())
                .containsExactly(tuple(11L, "1500.00"), tuple(12L, "5000.00"));
    }

    @Test
    void theNewestCreditIsNeverTakenBeforeAnOlderOneWithRoomLeft() {
        CreditContributions credits = new CreditContributions(List.of(payment(1L, 11L, MARCH_5, "3000.00"), payment(2L, 12L, MARCH_12, "5000.00")));

        List<ScheduledAllocation> schedule = schedule(credits, "8000.00", new PlannedInvoiceAllocation(7L, ars("2000.00")));

        assertThat(schedule.get(0).sources()).extracting(AllocationSourceShare::paymentId).containsExactly(11L);
        assertThat(schedule.get(0).effectiveDate()).isEqualTo(MARCH_5);
    }

    @Test
    void sourcesThatDoNotAddUpToTheAllocationAreRejected() {
        AllocationSourceShare share = new AllocationSourceShare(1L, 11L, null, ars("100.00"));

        assertThatThrownBy(() -> new ScheduledAllocation(7L, ars("101.00"), MARCH_5, List.of(share))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSourceNeedsExactlyOneOfPaymentOrCreditNote() {
        assertThatThrownBy(() -> new AllocationSourceShare(1L, 11L, 21L, ars("100.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AllocationSourceShare(1L, null, null, ars("100.00"))).isInstanceOf(IllegalArgumentException.class);
    }
}
