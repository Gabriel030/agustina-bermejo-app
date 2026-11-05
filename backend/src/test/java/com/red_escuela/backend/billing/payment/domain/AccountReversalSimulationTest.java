package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.account.domain.OldestOverdueFirstAllocationStrategy;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import com.red_escuela.backend.billing.account.exception.InsufficientAccountCreditException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects.InvoiceImpact;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ACCOUNT_ID;
import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.account.domain.AccountTestData.issuedInvoice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-094")
@Tag("D-206")
@Tag("D-414")
class AccountReversalSimulationTest {

    private static final LocalDate BEFORE_DUE = LocalDate.of(2027, 3, 12);
    private static final LocalDate AFTER_DUE = LocalDate.of(2027, 3, 20);
    private static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess("TEST");

    private long nextAllocationId = 1;

    private Invoice paidInvoice(long id, String amount) {
        Invoice invoice = issuedInvoice(id, amount);
        invoice.allocateFullInvoicePayment(ars(amount), BEFORE_DUE, ACTOR);
        return invoice;
    }

    private PaymentAllocation allocationOf(Invoice invoice, String amount) {
        PaymentAllocation allocation = PaymentAllocation.recordFullInvoiceAllocation(invoice, ars(amount), BEFORE_DUE, BEFORE_DUE, ACTOR);
        ReflectionTestUtils.setField(allocation, "id", nextAllocationId++);
        return allocation;
    }

    private AccountReversalSimulation simulationOf(String unallocated, String reserved, List<PaymentAllocation> allocations, List<Invoice> invoices, Set<Long> awaitingAuthorization) {
        return AccountReversalSimulation.of(new OldestOverdueFirstAllocationStrategy(), ACCOUNT_ID, ars(unallocated), ars(reserved), allocations, invoices, awaitingAuthorization);
    }

    private static NonNegativeMoney zero() {
        return ars("0.00");
    }

    @Test
    void reversingAnAllocationBeforeTheDueDateReopensTheInvoiceAsPendingWithItsFullOutstanding() {
        Invoice march = paidInvoice(1L, "80000.00");
        PaymentAllocation allocation = allocationOf(march, "80000.00");
        AccountReversalSimulation simulation = simulationOf("0.00", "0.00", List.of(allocation), List.of(march), Set.of());

        simulation.reverseAllocations(List.of(allocation), BEFORE_DUE, invoiceId -> 0);

        assertThat(simulation.effects().reopenedInvoices()).containsExactly(new InvoiceImpact(1L, InvoicePaymentStatus.PAID, InvoicePaymentStatus.PENDING, zero(),
                ars("80000.00"), ars("80000.00")));
        assertThat(simulation.currentStatusOf(1L)).isEqualTo(InvoicePaymentStatus.PENDING);
    }

    @Test
    void reversingAnAllocationAfterTheDueDateAndItsGraceReopensTheInvoiceAsOverdue() {
        Invoice march = paidInvoice(1L, "80000.00");
        PaymentAllocation allocation = allocationOf(march, "80000.00");
        AccountReversalSimulation simulation = simulationOf("0.00", "0.00", List.of(allocation), List.of(march), Set.of());

        simulation.reverseAllocations(List.of(allocation), AFTER_DUE, invoiceId -> 3);

        assertThat(simulation.currentStatusOf(1L)).isEqualTo(InvoicePaymentStatus.OVERDUE);
    }

    @Test
    void theGracePeriodOfEachInvoiceDecidesItsOwnReopenedStatus() {
        Invoice first = paidInvoice(1L, "10.00");
        Invoice second = paidInvoice(2L, "20.00");
        List<PaymentAllocation> allocations = List.of(allocationOf(first, "10.00"), allocationOf(second, "20.00"));
        AccountReversalSimulation simulation = simulationOf("0.00", "0.00", allocations, List.of(first, second), Set.of());

        simulation.reverseAllocations(allocations, LocalDate.of(2027, 3, 14), invoiceId -> invoiceId == 1L ? 0 : 5);

        assertThat(simulation.currentStatusOf(1L)).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(simulation.currentStatusOf(2L)).isEqualTo(InvoicePaymentStatus.PENDING);
    }

    @Test
    void theCreditReversedOfAnInvoiceIsTheSumOfItsReversedAllocations() {
        Invoice march = paidInvoice(1L, "80000.00");
        PaymentAllocation allocation = allocationOf(march, "80000.00");
        AccountReversalSimulation simulation = simulationOf("0.00", "0.00", List.of(allocation), List.of(march), Set.of());

        simulation.reverseAllocations(List.of(allocation, allocation), BEFORE_DUE, invoiceId -> 0);

        assertThat(simulation.effects().reopenedInvoices()).hasSize(1);
        assertThat(simulation.effects().reopenedInvoices().get(0).creditReversed()).isEqualTo(ars("160000.00"));
    }

    @Test
    void removingMoreCreditThanTheAccountHoldsIsRejected() {
        AccountReversalSimulation simulation = simulationOf("50.00", "0.00", List.of(), List.of(), Set.of());

        assertThatThrownBy(() -> simulation.removePaymentCredit(ars("50.01"))).isInstanceOf(InsufficientAccountCreditException.class);
        simulation.removePaymentCredit(ars("50.00"));
        simulation.allocateAccountCredit();
        assertThat(simulation.effects().settledInvoices()).isEmpty();
    }

    @Test
    void theReservedCreditMakesTheExtraReversalOfTheNewestAllocationsCoverTheCreditToRemove() {
        Invoice older = paidInvoice(1L, "100.00");
        Invoice newer = paidInvoice(2L, "100.00");
        List<PaymentAllocation> newestFirst = List.of(allocationOf(newer, "100.00"), allocationOf(older, "100.00"));
        AccountReversalSimulation simulation = simulationOf("100.00", "100.00", newestFirst, List.of(older, newer), Set.of());

        simulation.reverseNewestAllocationsUntilCreditCovers(ars("100.00"), BEFORE_DUE, invoiceId -> 0);

        assertThat(simulation.effects().reopenedInvoices()).extracting(InvoiceImpact::invoiceId).containsExactly(2L);
        simulation.removePaymentCredit(ars("100.00"));
    }

    @Test
    void theExtraReversalFailsWhenEvenReversingEveryAllocationDoesNotCoverTheCredit() {
        Invoice only = paidInvoice(1L, "100.00");
        AccountReversalSimulation simulation = simulationOf("0.00", "500.00", List.of(allocationOf(only, "100.00")), List.of(only), Set.of());

        assertThatThrownBy(() -> simulation.reverseNewestAllocationsUntilCreditCovers(ars("100.00"), BEFORE_DUE, invoiceId -> 0))
                .isInstanceOf(InsufficientAccountCreditException.class);
    }

    @Test
    void theCreditReleasedByTheReversalSettlesTheOldestOpenInvoicesThatItCoversInFull() {
        Invoice reopened = paidInvoice(1L, "100.00");
        Invoice openOne = issuedInvoice(2L, "60.00");
        Invoice openTwo = issuedInvoice(3L, "60.00");
        PaymentAllocation allocation = allocationOf(reopened, "100.00");
        AccountReversalSimulation simulation = simulationOf("0.00", "0.00", List.of(allocation), List.of(reopened, openOne, openTwo), Set.of());
        simulation.addPaymentCredit(ars("120.00"));

        simulation.allocateAccountCredit();

        assertThat(simulation.effects().settledInvoices()).extracting(InvoiceImpact::invoiceId).containsExactly(2L, 3L);
        assertThat(simulation.effects().settledInvoices().get(0)).isEqualTo(new InvoiceImpact(2L, InvoicePaymentStatus.PENDING, InvoicePaymentStatus.PAID, ars("60.00"), zero(),
                zero()));
    }

    @Test
    void theAllocationStopsAtTheFirstInvoiceThatDoesNotFit() {
        Invoice big = issuedInvoice(1L, "100.00");
        Invoice small = issuedInvoice(2L, "10.00");
        AccountReversalSimulation simulation = simulationOf("50.00", "0.00", List.of(), List.of(big, small), Set.of());

        simulation.allocateAccountCredit();

        assertThat(simulation.effects().settledInvoices()).isEmpty();
    }

    @Test
    void theReservedCreditIsNotAvailableToSettleInvoices() {
        Invoice open = issuedInvoice(1L, "100.00");
        AccountReversalSimulation simulation = simulationOf("100.00", "1.00", List.of(), List.of(open), Set.of());

        simulation.allocateAccountCredit();

        assertThat(simulation.effects().settledInvoices()).isEmpty();
        AccountReversalSimulation withEnoughNetCredit = simulationOf("101.00", "1.00", List.of(), List.of(open), Set.of());
        withEnoughNetCredit.allocateAccountCredit();
        assertThat(withEnoughNetCredit.effects().settledInvoices()).extracting(InvoiceImpact::invoiceId).containsExactly(1L);
    }

    @Test
    void anInvoiceWithAPendingAuthorizationRequestIsNotSettled() {
        Invoice awaiting = issuedInvoice(1L, "100.00");
        Invoice free = issuedInvoice(2L, "100.00");
        AccountReversalSimulation simulation = simulationOf("100.00", "0.00", List.of(), List.of(awaiting, free), Set.of(1L));

        simulation.allocateAccountCredit();

        assertThat(simulation.effects().settledInvoices()).extracting(InvoiceImpact::invoiceId).containsExactly(2L);
    }

    @Test
    void anInvoiceUnderReviewIsNotSettled() {
        Invoice underReview = issuedInvoice(1L, "100.00");
        underReview.markPaymentUnderReview(BEFORE_DUE, ACTOR, "receipt");
        AccountReversalSimulation simulation = simulationOf("100.00", "0.00", List.of(), List.of(underReview), Set.of());

        simulation.allocateAccountCredit();

        assertThat(simulation.effects().settledInvoices()).isEmpty();
    }

    @Test
    void aReinstatedLateFeeIsNotSettledWhenTheRemainingCreditDoesNotCoverTheOlderOrigin() {
        Invoice origin = paidInvoice(1L, "100.00");
        PaymentAllocation allocation = allocationOf(origin, "100.00");
        AccountReversalSimulation simulation = simulationOf("50.00", "0.00", List.of(allocation), List.of(origin), Set.of());
        simulation.reverseAllocations(List.of(allocation), AFTER_DUE, invoiceId -> 0);
        simulation.removePaymentCredit(ars("100.00"));

        simulation.issueLateFeeDebitNote(1L, LocalDate.of(2027, 3, 14), LocalDate.of(2027, 3, 16), 3, ars("600.00"), AFTER_DUE, AFTER_DUE.plusDays(10));

        assertThat(simulation.effects().settledInvoices()).isEmpty();
        assertThat(simulation.effects().lateFeeReinstatements()).hasSize(1);
        assertThat(simulation.effects().lateFeeReinstatements().get(0).settledByAccountCredit()).isFalse();
        assertThat(simulation.currentStatusOf(1L)).isEqualTo(InvoicePaymentStatus.OVERDUE);
    }

    @Test
    void aReinstatedLateFeeIsSettledWhenTheCreditCoversTheOriginAndTheNote() {
        Invoice origin = paidInvoice(1L, "100.00");
        PaymentAllocation allocation = allocationOf(origin, "100.00");
        AccountReversalSimulation simulation = simulationOf("700.00", "0.00", List.of(allocation), List.of(origin), Set.of());
        simulation.reverseAllocations(List.of(allocation), AFTER_DUE, invoiceId -> 0);
        simulation.removePaymentCredit(ars("100.00"));

        simulation.issueLateFeeDebitNote(1L, LocalDate.of(2027, 3, 14), LocalDate.of(2027, 3, 16), 3, ars("600.00"), AFTER_DUE, AFTER_DUE.plusDays(10));

        assertThat(simulation.effects().settledInvoices()).extracting(InvoiceImpact::invoiceId).containsExactly(1L);
        assertThat(simulation.effects().lateFeeReinstatements().get(0).settledByAccountCredit()).isTrue();
        assertThat(simulation.currentStatusOf(1L)).isEqualTo(InvoicePaymentStatus.PAID);
    }

    @Test
    void theReopenedInvoicesAreListedInAscendingOrder() {
        List<PaymentAllocation> allocations = new ArrayList<>();
        List<Invoice> invoices = new ArrayList<>();
        for (long id : new long[]{3L, 1L, 2L}) {
            Invoice invoice = paidInvoice(id, "10.00");
            invoices.add(invoice);
            allocations.add(allocationOf(invoice, "10.00"));
        }
        AccountReversalSimulation simulation = simulationOf("0.00", "0.00", allocations, invoices, Set.of());

        simulation.reverseAllocations(allocations, BEFORE_DUE, invoiceId -> 0);

        assertThat(simulation.reopenedInvoiceIdsInAscendingOrder()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void theSimulationDoesNotChangeTheInvoicesItWasBuiltFrom() {
        Invoice march = paidInvoice(1L, "80000.00");
        int changesBefore = march.getStatusChanges().size();
        PaymentAllocation allocation = allocationOf(march, "80000.00");
        AccountReversalSimulation simulation = simulationOf("0.00", "0.00", List.of(allocation), List.of(march), Set.of());

        simulation.reverseAllocations(List.of(allocation), AFTER_DUE, invoiceId -> 0);
        simulation.addPaymentCredit(ars("80000.00"));
        simulation.allocateAccountCredit();

        assertThat(march.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(march.getAmounts().outstandingAmount()).isEqualTo(zero());
        assertThat(march.getStatusChanges()).hasSize(changesBefore);
    }
}
