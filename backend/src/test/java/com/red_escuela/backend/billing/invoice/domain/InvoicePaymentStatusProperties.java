package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-136")
@Tag("D-165")
@Tag("D-174")
class InvoicePaymentStatusProperties {

    private static final LocalDate BEFORE = DUE_DATE;
    private static final LocalDate AFTER = DUE_DATE.plusDays(1);

    private static final List<TransitionExpectation> LITERAL_TABLE = InvoicePaymentStatusTransitionTest.expectedTransitions();

    @Provide
    Arbitrary<List<Integer>> operations() {
        return Arbitraries.integers().between(0, 11).list().ofMinSize(1).ofMaxSize(25);
    }

    private static Optional<InvoicePaymentStatus> literalTarget(InvoicePaymentStatus from, InvoicePaymentEvent event) {
        return LITERAL_TABLE.stream()
                .filter(e -> e.fromState() == from && e.event() == event && e.isAllowed())
                .map(e -> (InvoicePaymentStatus) e.toState())
                .findFirst();
    }

    private static final InvoicePaymentEvent[] INTENDED = {
            InvoicePaymentEvent.BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME,
            InvoicePaymentEvent.BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED,
            InvoicePaymentEvent.BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED,
            InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED,
            InvoicePaymentEvent.PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED,
            InvoicePaymentEvent.PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED,
            InvoicePaymentEvent.DUE_DATE_PASSED,
            InvoicePaymentEvent.CANCELLATION_CREDIT_NOTE_APPLIED,
            InvoicePaymentEvent.DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE,
            InvoicePaymentEvent.PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED,
            InvoicePaymentEvent.CREDIT_NOTE_APPLIED_TO_PAID_INVOICE,
            InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED};

    private static boolean rejectionIsJustified(int operation, InvoicePaymentStatus before, InvoiceAmounts amountsBefore, NonNegativeMoney creditableBefore, RuntimeException rejected) {
        InvoicePaymentEvent intended = INTENDED[operation];
        if (literalTarget(before, intended).isEmpty()) {
            return true;
        }
        boolean reversal = operation == 4 || operation == 5;
        if (reversal && !amountsBefore.hasPaidAmount()) {
            return true;
        }
        if (operation >= 7 && operation <= 10 && rejected instanceof com.red_escuela.backend.billing.invoice.exception.CreditNoteExceedsCreditableAmountException) {
            NonNegativeMoney requested = operation == 7 || operation == 8 ? amountsBefore.outstandingAmount() : ars("1.00");
            return requested.value().isGreaterThan(creditableBefore.value());
        }
        if (operation == 11 && !amountsBefore.outstandingAmount().equals(ars("1.00"))) {
            return rejected instanceof com.red_escuela.backend.billing.invoice.exception.PartialPaymentNotAllowedException;
        }
        return false;
    }

    private static InvoicePaymentEvent apply(Invoice invoice, int operation) {
        switch (operation) {
            case 0 -> invoice.markPaymentUnderReview(BEFORE, ACTOR, "r");
            case 1 -> invoice.releasePaymentReview(BEFORE, 0, ACTOR, "r");
            case 2 -> invoice.releasePaymentReview(AFTER, 0, ACTOR, "r");
            case 3 -> invoice.allocateFullInvoicePayment(invoice.getAmounts().outstandingAmount(), BEFORE, ACTOR);
            case 4 -> invoice.reversePaymentAllocation(BEFORE, 0, ACTOR, "r");
            case 5 -> invoice.reversePaymentAllocation(AFTER, 0, ACTOR, "r");
            case 6 -> invoice.markInvoiceAsOverdue(AFTER, ACTOR);
            case 7 -> invoice.applyCreditNote(creditNote(CreditNoteApplicationKind.CANCELLATION, invoice.getAmounts().outstandingAmount().value().amount().toPlainString(), "0.00"), BEFORE, ACTOR);
            case 8 -> invoice.applyCreditNote(creditNote(CreditNoteApplicationKind.DEBT_REDUCTION, invoice.getAmounts().outstandingAmount().value().amount().toPlainString(), "0.00"), BEFORE, ACTOR);
            case 9 -> invoice.applyCreditNote(creditNote(CreditNoteApplicationKind.DEBT_REDUCTION, "1.00", "0.00"), BEFORE, ACTOR);
            case 10 -> invoice.applyCreditNote(creditNote(CreditNoteApplicationKind.DEBT_REDUCTION, "0.00", "1.00"), BEFORE, ACTOR);
            default -> invoice.allocateFullInvoicePayment(ars("1.00"), BEFORE, ACTOR);
        }
        return InvoicePaymentEvent.valueOf(invoice.getStatusChanges().get(invoice.getStatusChanges().size() - 1).getEvent());
    }

    @Property(seed = "20271007")
    void everyStepEitherFollowsTheLiteralTableWithCoherentAmountsOrFailsWithoutChanges(@ForAll("operations") List<Integer> operations) {
        Invoice invoice = issuedMonthly("80000.00");
        for (int operation : operations) {
            InvoicePaymentStatus before = invoice.getPaymentStatus();
            InvoiceAmounts amountsBefore = invoice.getAmounts();
            var creditedBefore = invoice.getLines().get(0).getCreditedAmount();
            NonNegativeMoney creditableBefore = invoice.getLines().get(0).calculateCreditableAmount();
            int rows = invoice.getStatusChanges().size();
            try {
                InvoicePaymentEvent event = apply(invoice, operation);
                assertThat(literalTarget(before, event)).contains(invoice.getPaymentStatus());
                assertThat(invoice.getStatusChanges()).hasSize(rows + 1);
            } catch (RuntimeException rejected) {
                assertThat(rejectionIsJustified(operation, before, amountsBefore, creditableBefore, rejected))
                        .as("operation %s rejected from %s with %s: %s", operation, before, amountsBefore, rejected).isTrue();
                assertThat(invoice.getPaymentStatus()).isEqualTo(before);
                assertThat(invoice.getAmounts()).isEqualTo(amountsBefore);
                assertThat(invoice.getLines().get(0).getCreditedAmount()).isEqualTo(creditedBefore);
                assertThat(invoice.getStatusChanges()).hasSize(rows);
            }
            assertCoherent(invoice);
        }
    }

    private static void assertCoherent(Invoice invoice) {
        InvoiceAmounts amounts = invoice.getAmounts();
        assertThat(amounts.hasPaidAmount() && amounts.hasOutstandingAmount()).isFalse();
        assertThat(amounts.outstandingAmount().value().isNegative()).isFalse();
        switch (invoice.getPaymentStatus()) {
            case PENDING, PAYMENT_UNDER_REVIEW, OVERDUE -> assertThat(!amounts.hasPaidAmount() && amounts.hasOutstandingAmount()).isTrue();
            case PAID -> assertThat(amounts.hasOutstandingAmount()).isFalse();
            case CANCELLED -> assertThat(!amounts.hasPaidAmount() && !amounts.hasOutstandingAmount() && amounts.creditedAmount().equals(amounts.total())).isTrue();
        }
        assertThat(invoice.getLines().get(0).calculateCreditableAmount().value().isNegative()).isFalse();
    }
}
