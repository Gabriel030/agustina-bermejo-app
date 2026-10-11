package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-139")
class NewestAllocationsReversalSelectionTest {

    private static final LocalDate DAY = LocalDate.of(2027, 4, 20);
    private static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess("TEST");

    private static PaymentAllocation allocation(long invoiceId, String amount) {
        return PaymentAllocation.recordFullInvoiceAllocation(AccountTestData.issuedInvoice(invoiceId, amount), ars(amount), DAY, DAY, ACTOR);
    }

    private static List<PaymentAllocation> newestFirst() {
        return List.of(allocation(3L, "50.00"), allocation(2L, "30.00"), allocation(1L, "20.00"));
    }

    private static List<Long> select(String unallocated, String toRemove) {
        return NewestAllocationsReversalSelection.selectAllocationsToReverse(newestFirst(), ars(unallocated), ars(toRemove)).stream()
                .map(PaymentAllocation::getInvoiceId).toList();
    }

    @Test
    void nothingIsSelectedWhenTheUnallocatedCreditAlreadyCoversWhatMustBeRemoved() {
        assertThat(select("100.00", "40.00")).isEmpty();
    }

    @Test
    void nothingIsSelectedWhenTheUnallocatedCreditEqualsWhatMustBeRemoved() {
        assertThat(select("40.00", "40.00")).isEmpty();
    }

    @Test
    void oneCentMissingSelectsTheNewestAllocation() {
        assertThat(select("40.00", "40.01")).containsExactly(3L);
    }

    @Test
    void selectionStopsAsSoonAsTheRestoredCreditCovers() {
        assertThat(select("10.00", "60.00")).containsExactly(3L);
        assertThat(select("10.00", "60.01")).containsExactly(3L, 2L);
    }

    @Test
    void whenTheCoverageIsReachedExactlyWithTheLastAllocationAllAreSelected() {
        assertThat(select("0.00", "100.00")).containsExactly(3L, 2L, 1L);
    }

    @Test
    void whenEvenEverythingDoesNotCoverAllAllocationsAreSelected() {
        assertThat(select("0.00", "100.01")).containsExactly(3L, 2L, 1L);
    }

    @Test
    void theSelectionKeepsTheNewestToOldestOrderOfTheInput() {
        List<PaymentAllocation> input = newestFirst();

        assertThat(NewestAllocationsReversalSelection.selectAllocationsToReverse(input, ars("0.00"), ars("100.00"))).containsExactlyElementsOf(input);
    }

    @Test
    void anEmptyListSelectsNothing() {
        assertThat(NewestAllocationsReversalSelection.selectAllocationsToReverse(List.of(), ars("0.00"), ars("10.00"))).isEmpty();
    }

    @Test
    void removingZeroSelectsNothing() {
        assertThat(select("0.00", "0.00")).isEmpty();
    }
}
