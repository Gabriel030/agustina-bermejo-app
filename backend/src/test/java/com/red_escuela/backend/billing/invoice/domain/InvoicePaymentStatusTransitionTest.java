package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent.*;
import static com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus.*;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation.allows;
import static com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation.rejects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-136")
@Tag("D-165")
@Tag("D-153")
@Tag("D-154")
@Tag("D-091")
@Tag("D-127")
@Tag("D-167")
@Tag("D-150")
@Tag("D-094")
@Tag("D-051")
@ExhaustiveTransitionExpectations(machine = InvoicePaymentStatusStateMachine.class)
class InvoicePaymentStatusTransitionTest {

    private static final Class<IllegalInvoicePaymentStatusTransitionException> ILLEGAL = IllegalInvoicePaymentStatusTransitionException.class;
    private static final LocalDate BEFORE_DUE = DUE_DATE;
    private static final LocalDate AFTER_DUE = DUE_DATE.plusDays(1);

    static List<TransitionExpectation> expectedTransitions() {
        return List.of(
                allows(null, ISSUED_WITH_AMOUNT_DUE, PENDING),
                allows(null, ISSUED_WITH_ZERO_TOTAL, PAID),
                rejects(null, BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME, ILLEGAL),
                rejects(null, BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED, ILLEGAL),
                rejects(null, BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED, ILLEGAL),
                rejects(null, FULL_PAYMENT_ALLOCATED, ILLEGAL),
                rejects(null, PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED, ILLEGAL),
                rejects(null, PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED, ILLEGAL),
                rejects(null, DUE_DATE_PASSED, ILLEGAL),
                rejects(null, CANCELLATION_CREDIT_NOTE_APPLIED, ILLEGAL),
                rejects(null, DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE, ILLEGAL),
                rejects(null, PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED, ILLEGAL),
                rejects(null, CREDIT_NOTE_APPLIED_TO_PAID_INVOICE, ILLEGAL),
                rejects(PENDING, ISSUED_WITH_AMOUNT_DUE, ILLEGAL),
                rejects(PENDING, ISSUED_WITH_ZERO_TOTAL, ILLEGAL),
                allows(PENDING, BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME, PAYMENT_UNDER_REVIEW),
                rejects(PENDING, BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED, ILLEGAL),
                rejects(PENDING, BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED, ILLEGAL),
                allows(PENDING, FULL_PAYMENT_ALLOCATED, PAID),
                rejects(PENDING, PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED, ILLEGAL),
                rejects(PENDING, PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED, ILLEGAL),
                allows(PENDING, DUE_DATE_PASSED, OVERDUE),
                allows(PENDING, CANCELLATION_CREDIT_NOTE_APPLIED, CANCELLED),
                allows(PENDING, DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE, PAID),
                allows(PENDING, PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED, PENDING),
                rejects(PENDING, CREDIT_NOTE_APPLIED_TO_PAID_INVOICE, ILLEGAL),
                rejects(PAYMENT_UNDER_REVIEW, ISSUED_WITH_AMOUNT_DUE, ILLEGAL),
                rejects(PAYMENT_UNDER_REVIEW, ISSUED_WITH_ZERO_TOTAL, ILLEGAL),
                rejects(PAYMENT_UNDER_REVIEW, BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME, ILLEGAL),
                allows(PAYMENT_UNDER_REVIEW, BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED, PENDING),
                allows(PAYMENT_UNDER_REVIEW, BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED, OVERDUE),
                allows(PAYMENT_UNDER_REVIEW, FULL_PAYMENT_ALLOCATED, PAID),
                rejects(PAYMENT_UNDER_REVIEW, PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED, ILLEGAL),
                rejects(PAYMENT_UNDER_REVIEW, PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED, ILLEGAL),
                rejects(PAYMENT_UNDER_REVIEW, DUE_DATE_PASSED, ILLEGAL),
                rejects(PAYMENT_UNDER_REVIEW, CANCELLATION_CREDIT_NOTE_APPLIED, ILLEGAL),
                rejects(PAYMENT_UNDER_REVIEW, DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE, ILLEGAL),
                rejects(PAYMENT_UNDER_REVIEW, PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED, ILLEGAL),
                rejects(PAYMENT_UNDER_REVIEW, CREDIT_NOTE_APPLIED_TO_PAID_INVOICE, ILLEGAL),
                rejects(PAID, ISSUED_WITH_AMOUNT_DUE, ILLEGAL),
                rejects(PAID, ISSUED_WITH_ZERO_TOTAL, ILLEGAL),
                rejects(PAID, BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME, ILLEGAL),
                rejects(PAID, BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED, ILLEGAL),
                rejects(PAID, BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED, ILLEGAL),
                rejects(PAID, FULL_PAYMENT_ALLOCATED, ILLEGAL),
                allows(PAID, PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED, PENDING),
                allows(PAID, PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED, OVERDUE),
                rejects(PAID, DUE_DATE_PASSED, ILLEGAL),
                rejects(PAID, CANCELLATION_CREDIT_NOTE_APPLIED, ILLEGAL),
                rejects(PAID, DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE, ILLEGAL),
                rejects(PAID, PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED, ILLEGAL),
                allows(PAID, CREDIT_NOTE_APPLIED_TO_PAID_INVOICE, PAID),
                rejects(OVERDUE, ISSUED_WITH_AMOUNT_DUE, ILLEGAL),
                rejects(OVERDUE, ISSUED_WITH_ZERO_TOTAL, ILLEGAL),
                rejects(OVERDUE, BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME, ILLEGAL),
                rejects(OVERDUE, BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED, ILLEGAL),
                rejects(OVERDUE, BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED, ILLEGAL),
                allows(OVERDUE, FULL_PAYMENT_ALLOCATED, PAID),
                rejects(OVERDUE, PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED, ILLEGAL),
                rejects(OVERDUE, PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED, ILLEGAL),
                rejects(OVERDUE, DUE_DATE_PASSED, ILLEGAL),
                allows(OVERDUE, CANCELLATION_CREDIT_NOTE_APPLIED, CANCELLED),
                allows(OVERDUE, DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE, PAID),
                allows(OVERDUE, PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED, OVERDUE),
                rejects(OVERDUE, CREDIT_NOTE_APPLIED_TO_PAID_INVOICE, ILLEGAL),
                rejects(CANCELLED, ISSUED_WITH_AMOUNT_DUE, ILLEGAL),
                rejects(CANCELLED, ISSUED_WITH_ZERO_TOTAL, ILLEGAL),
                rejects(CANCELLED, BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME, ILLEGAL),
                rejects(CANCELLED, BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED, ILLEGAL),
                rejects(CANCELLED, BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED, ILLEGAL),
                rejects(CANCELLED, FULL_PAYMENT_ALLOCATED, ILLEGAL),
                rejects(CANCELLED, PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED, ILLEGAL),
                rejects(CANCELLED, PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED, ILLEGAL),
                rejects(CANCELLED, DUE_DATE_PASSED, ILLEGAL),
                rejects(CANCELLED, CANCELLATION_CREDIT_NOTE_APPLIED, ILLEGAL),
                rejects(CANCELLED, DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE, ILLEGAL),
                rejects(CANCELLED, PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED, ILLEGAL),
                rejects(CANCELLED, CREDIT_NOTE_APPLIED_TO_PAID_INVOICE, ILLEGAL));
    }

    static Stream<TransitionExpectation> everyPair() {
        return expectedTransitions().stream();
    }

    private static Invoice invoiceIn(InvoicePaymentStatus status) {
        if (status == null) {
            return monthlyDraft("80000.00");
        }
        Invoice invoice = issuedMonthly("80000.00");
        switch (status) {
            case PENDING -> {
            }
            case PAYMENT_UNDER_REVIEW -> invoice.markPaymentUnderReview(BEFORE_DUE, ACTOR, "comprobante");
            case PAID -> invoice.allocateFullInvoicePayment(ars("80000.00"), BEFORE_DUE, ACTOR);
            case OVERDUE -> invoice.markInvoiceAsOverdue(AFTER_DUE, ACTOR);
            case CANCELLED -> invoice.applyCreditNote(creditNote(CreditNoteApplicationKind.CANCELLATION, "80000.00", "0.00"), BEFORE_DUE, ACTOR);
        }
        return invoice;
    }

    private static void applyThroughTheAggregate(Invoice invoice, InvoicePaymentEvent event) {
        switch (event) {
            case ISSUED_WITH_AMOUNT_DUE, ISSUED_WITH_ZERO_TOTAL -> issue(invoice);
            case BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME -> invoice.markPaymentUnderReview(BEFORE_DUE, ACTOR, "comprobante");
            case BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED -> invoice.releasePaymentReview(BEFORE_DUE, 0, ACTOR, "rechazado");
            case BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED -> invoice.releasePaymentReview(AFTER_DUE, 0, ACTOR, "rechazado");
            case FULL_PAYMENT_ALLOCATED -> invoice.allocateFullInvoicePayment(ars("80000.00"), BEFORE_DUE, ACTOR);
            case PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED -> invoice.reversePaymentAllocation(BEFORE_DUE, 0, ACTOR, "reversion");
            case PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED -> invoice.reversePaymentAllocation(AFTER_DUE, 0, ACTOR, "reversion");
            case DUE_DATE_PASSED -> invoice.markInvoiceAsOverdue(AFTER_DUE, ACTOR);
            case CANCELLATION_CREDIT_NOTE_APPLIED -> invoice.applyCreditNote(creditNote(CreditNoteApplicationKind.CANCELLATION, "80000.00", "0.00"), BEFORE_DUE, ACTOR);
            case DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE -> invoice.applyCreditNote(creditNote(CreditNoteApplicationKind.DEBT_REDUCTION, "80000.00", "0.00"), BEFORE_DUE, ACTOR);
            case PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED -> invoice.applyCreditNote(creditNote(CreditNoteApplicationKind.DEBT_REDUCTION, "30000.00", "0.00"), BEFORE_DUE, ACTOR);
            case CREDIT_NOTE_APPLIED_TO_PAID_INVOICE -> invoice.applyCreditNote(creditNote(CreditNoteApplicationKind.DEBT_REDUCTION, "0.00", "10000.00"), BEFORE_DUE, ACTOR);
        }
    }

    private static long paymentRows(Invoice invoice) {
        return invoice.getStatusChanges().stream().filter(c -> c.getStatusKind() == InvoiceStatusKind.PAYMENT).count();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPair")
    void everyStateAndEventPairBehavesAsTheTableSays(TransitionExpectation expectation) {
        InvoicePaymentStatus from = (InvoicePaymentStatus) expectation.fromState();
        InvoicePaymentEvent event = (InvoicePaymentEvent) expectation.event();
        Invoice invoice = from == null && event == ISSUED_WITH_ZERO_TOTAL ? zeroTotalDraft() : invoiceIn(from);
        long rows = paymentRows(invoice);
        var amounts = invoice.getAmounts();
        if (expectation.isAllowed()) {
            applyThroughTheAggregate(invoice, event);
            assertThat(invoice.getPaymentStatus()).isEqualTo(expectation.toState());
            assertThat(paymentRows(invoice)).isEqualTo(rows + 1);
            InvoiceStatusChange last = invoice.getStatusChanges().stream()
                    .filter(c -> c.getStatusKind() == InvoiceStatusKind.PAYMENT).reduce((a, b) -> b).orElseThrow();
            assertThat(last.getFromStatus()).isEqualTo(from == null ? null : from.name());
            assertThat(last.getToStatus()).isEqualTo(expectation.toState().name());
            assertThat(last.getEvent()).isEqualTo(event.name());
            assertThat(last.getActorProcess()).isEqualTo("TEST");
        } else {
            assertThatThrownBy(() -> invoice.changePaymentStatus(event, amounts, BEFORE_DUE, ACTOR, null)).isInstanceOf(expectation.rejection());
            assertThat(invoice.getPaymentStatus()).isEqualTo(from);
            assertThat(paymentRows(invoice)).isEqualTo(rows);
            assertThat(invoice.getAmounts()).isEqualTo(amounts);
        }
    }
}
