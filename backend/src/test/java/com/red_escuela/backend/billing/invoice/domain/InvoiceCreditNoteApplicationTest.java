package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.domain.event.InvoiceCancelledEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidEvent;
import com.red_escuela.backend.billing.invoice.exception.CreditNoteExceedsCreditableAmountException;
import com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.red_escuela.backend.billing.invoice.domain.CreditNoteApplicationKind.CANCELLATION;
import static com.red_escuela.backend.billing.invoice.domain.CreditNoteApplicationKind.DEBT_REDUCTION;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-127")
@Tag("D-167")
@Tag("D-091")
@Tag("D-165")
class InvoiceCreditNoteApplicationTest {

    private static Invoice pending() {
        return withId(issuedMonthly("80000.00"), 3L);
    }

    @Test
    void aCancellationForTheWholeOutstandingAmountCancelsTheInvoiceKeepingLinesAndTotal() {
        Invoice invoice = pending();

        invoice.applyCreditNote(creditNote(CANCELLATION, "80000.00", "0.00"), DUE_DATE, ACTOR);

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.CANCELLED);
        assertThat(invoice.getAmounts().creditedAmount()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getAmounts().total()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getAmounts().outstandingAmount()).isEqualTo(ars("0.00"));
        assertThat(invoice.getLines()).hasSize(1);
        assertThat(invoice.getLines().get(0).getAmount()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getLines().get(0).getCreditedAmount()).isEqualTo(ars("80000.00"));
        InvoiceStatusChange last = invoice.getStatusChanges().get(invoice.getStatusChanges().size() - 1);
        assertThat(last.getFromStatus()).isEqualTo("PENDING");
        assertThat(last.getToStatus()).isEqualTo("CANCELLED");
        assertThat(last.getEvent()).isEqualTo("CANCELLATION_CREDIT_NOTE_APPLIED");
        assertThat(last.getReason()).isEqualTo("CANCELLATION");
        invoice.pullRecordedDomainEvents();
    }

    @Test
    void aCancellationPublishesTheCancelledEvent() {
        Invoice invoice = pending();
        invoice.pullRecordedDomainEvents();

        invoice.applyCreditNote(creditNote(CANCELLATION, "80000.00", "0.00"), DUE_DATE, ACTOR);

        assertThat(invoice.pullRecordedDomainEvents()).containsExactly(new InvoiceCancelledEvent(3L, DUE_DATE));
    }

    @Test
    void aCancellationThatDoesNotCoverTheOutstandingAmountIsRejected() {
        Invoice invoice = pending();

        assertThatThrownBy(() -> invoice.applyCreditNote(creditNote(CANCELLATION, "30000.00", "0.00"), DUE_DATE, ACTOR))
                .isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(invoice.getAmounts().creditedAmount()).isEqualTo(ars("0.00"));
        assertThat(invoice.getLines().get(0).getCreditedAmount()).isEqualTo(ars("0.00"));
    }

    @Test
    void aDebtReductionForTheWholeAmountPaysTheInvoiceWithoutAllocations() {
        Invoice invoice = pending();
        invoice.pullRecordedDomainEvents();

        invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "80000.00", "0.00"), DUE_DATE, ACTOR);

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(invoice.getAmounts().paidAmount()).isEqualTo(ars("0.00"));
        assertThat(invoice.getAmounts().creditedAmount()).isEqualTo(ars("80000.00"));
        assertThat(invoice.pullRecordedDomainEvents()).containsExactly(
                new InvoicePaidEvent(3L, 1L, InvoicePaymentEvent.DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE, DUE_DATE));
    }

    @Test
    void aPartialDebtReductionKeepsTheInvoicePendingWithTheRemainder() {
        Invoice invoice = pending();
        invoice.pullRecordedDomainEvents();

        invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "30000.00", "0.00"), DUE_DATE, ACTOR);

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(invoice.getAmounts().outstandingAmount()).isEqualTo(ars("50000.00"));
        assertThat(invoice.getAmounts().creditedAmount()).isEqualTo(ars("30000.00"));
        assertThat(invoice.pullRecordedDomainEvents()).isEmpty();
        invoice.allocateFullInvoicePayment(ars("50000.00"), DUE_DATE, ACTOR);
        assertThat(invoice.getAmounts().paidAmount()).isEqualTo(ars("50000.00"));
    }

    @Test
    void aDebtReductionThatOffsetsNothingOnAnUnpaidInvoiceIsRejected() {
        Invoice invoice = pending();

        assertThatThrownBy(() -> invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "0.00", "10000.00"), DUE_DATE, ACTOR))
                .isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
        assertThat(invoice.getLines().get(0).getCreditedAmount()).isEqualTo(ars("0.00"));
    }

    @Test
    void aReductionOverTheOutstandingAmountIsRejectedWithoutChanges() {
        Invoice invoice = pending();

        assertThatThrownBy(() -> invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "80000.01", "0.00"), DUE_DATE, ACTOR))
                .isInstanceOf(CreditNoteExceedsCreditableAmountException.class);
        assertThatThrownBy(() -> invoice.applyCreditNote(creditNote(CANCELLATION, "80000.01", "0.00"), DUE_DATE, ACTOR))
                .isInstanceOf(CreditNoteExceedsCreditableAmountException.class);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(invoice.getAmounts().creditedAmount()).isEqualTo(ars("0.00"));
    }

    @Test
    void aPaidInvoiceTakesFreeCreditUpToTheCreditableAmountOnTheLine() {
        Invoice invoice = pending();
        invoice.allocateFullInvoicePayment(ars("80000.00"), DUE_DATE, ACTOR);

        invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "0.00", "80000.00"), DUE_DATE, ACTOR);

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(invoice.getLines().get(0).getFreeCreditedAmount()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getLines().get(0).getCreditedAmount()).isEqualTo(ars("0.00"));
        assertThat(invoice.getLines().get(0).calculateCreditableAmount()).isEqualTo(ars("0.00"));
        assertThat(invoice.getAmounts().creditedAmount()).isEqualTo(ars("0.00"));
        assertThat(invoice.getStatusChanges().get(invoice.getStatusChanges().size() - 1).getEvent()).isEqualTo("CREDIT_NOTE_APPLIED_TO_PAID_INVOICE");
    }

    @Test
    void freeCreditAfterAnOffsetReductionIsLimitedToWhatTheLineStillHasUncredited() {
        Invoice invoice = pending();
        invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "40000.00", "0.00"), DUE_DATE, ACTOR);
        invoice.allocateFullInvoicePayment(ars("40000.00"), DUE_DATE, ACTOR);

        assertThatThrownBy(() -> invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "0.00", "80000.00"), DUE_DATE, ACTOR))
                .isInstanceOf(CreditNoteExceedsCreditableAmountException.class);
        assertThatThrownBy(() -> invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "0.00", "40000.01"), DUE_DATE, ACTOR))
                .isInstanceOf(CreditNoteExceedsCreditableAmountException.class);
        invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "0.00", "40000.00"), DUE_DATE, ACTOR);

        assertThat(invoice.getLines().get(0).calculateCreditableAmount()).isEqualTo(ars("0.00"));
    }

    @Test
    void freeCreditOneCentOverTheCreditableAmountIsRejected() {
        Invoice invoice = pending();
        invoice.allocateFullInvoicePayment(ars("80000.00"), DUE_DATE, ACTOR);

        assertThatThrownBy(() -> invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "0.00", "80000.01"), DUE_DATE, ACTOR))
                .isInstanceOf(CreditNoteExceedsCreditableAmountException.class);
        assertThat(invoice.getLines().get(0).getCreditedAmount()).isEqualTo(ars("0.00"));
    }

    @Test
    void aPaidInvoiceRejectsAnOffsetAmount() {
        Invoice invoice = pending();
        invoice.allocateFullInvoicePayment(ars("80000.00"), DUE_DATE, ACTOR);

        assertThatThrownBy(() -> invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "10.00", "0.00"), DUE_DATE, ACTOR))
                .isInstanceOf(CreditNoteExceedsCreditableAmountException.class);
    }

    @Test
    void anInvoiceUnderReviewIsNotCreditedUntilTheReviewIsResolved() {
        Invoice invoice = pending();
        invoice.markPaymentUnderReview(DUE_DATE, ACTOR, "comprobante");

        assertThatThrownBy(() -> invoice.applyCreditNote(creditNote(CANCELLATION, "80000.00", "0.00"), DUE_DATE, ACTOR))
                .isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
        assertThatThrownBy(() -> invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "80000.00", "0.00"), DUE_DATE, ACTOR))
                .isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
        assertThatThrownBy(() -> invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "30000.00", "0.00"), DUE_DATE, ACTOR))
                .isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);
    }

    @Test
    void anOverdueInvoiceKeepsBeingOverdueAfterAPartialReduction() {
        Invoice invoice = pending();
        invoice.markInvoiceAsOverdue(DUE_DATE.plusDays(1), ACTOR);

        invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "30000.00", "0.00"), DUE_DATE.plusDays(2), ACTOR);

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(invoice.getAmounts().outstandingAmount()).isEqualTo(ars("50000.00"));
    }

    @Test
    void creditsDistributedOverSeveralLinesRequireAllOfThemToBeCreditable() {
        Invoice annual = annualDraft(2);
        issue(annual);
        InvoiceCreditNoteApplication application = new InvoiceCreditNoteApplication(DEBT_REDUCTION, ars("1500.00"), ars("0.00"),
                List.of(new InvoiceLineCredit(1, ars("1000.00")), new InvoiceLineCredit(2, ars("500.00"))));

        annual.applyCreditNote(application, DUE_DATE, ACTOR);

        assertThat(annual.getLines().get(0).getCreditedAmount()).isEqualTo(ars("1000.00"));
        assertThat(annual.getLines().get(1).getCreditedAmount()).isEqualTo(ars("500.00"));
        InvoiceCreditNoteApplication tooMuchOnSecond = new InvoiceCreditNoteApplication(DEBT_REDUCTION, ars("500.00"), ars("0.00"),
                List.of(new InvoiceLineCredit(1, ars("0.01")), new InvoiceLineCredit(2, ars("499.99"))));
        assertThatThrownBy(() -> annual.applyCreditNote(tooMuchOnSecond, DUE_DATE, ACTOR)).isInstanceOf(CreditNoteExceedsCreditableAmountException.class);
        assertThat(annual.getLines().get(0).getCreditedAmount()).isEqualTo(ars("1000.00"));
    }

    @Test
    void aCreditOnAnUnknownLineIsAProgrammingError() {
        Invoice invoice = pending();
        InvoiceCreditNoteApplication application = new InvoiceCreditNoteApplication(DEBT_REDUCTION, ars("10.00"), ars("0.00"),
                List.of(new InvoiceLineCredit(2, ars("10.00"))));

        assertThatThrownBy(() -> invoice.applyCreditNote(application, DUE_DATE, ACTOR)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnissuedInvoiceRejectsCreditNotes() {
        Invoice draft = monthlyDraft("80000.00");

        assertThatThrownBy(() -> draft.applyCreditNote(creditNote(CANCELLATION, "80000.00", "0.00"), DUE_DATE, ACTOR))
                .isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
        assertThatThrownBy(() -> draft.applyCreditNote(creditNote(DEBT_REDUCTION, "10.00", "0.00"), DUE_DATE, ACTOR))
                .isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
    }

    @Test
    void applicationAndLineCreditValuesAreValidated() {
        assertThatThrownBy(() -> new InvoiceCreditNoteApplication(DEBT_REDUCTION, ars("0.00"), ars("0.00"), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceCreditNoteApplication(DEBT_REDUCTION, ars("10.00"), ars("0.00"), List.of(new InvoiceLineCredit(1, ars("9.99")))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceCreditNoteApplication(DEBT_REDUCTION, ars("10.00"), ars("0.00"),
                List.of(new InvoiceLineCredit(1, ars("5.00")), new InvoiceLineCredit(1, ars("5.00"))))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceCreditNoteApplication(null, ars("1.00"), ars("0.00"), List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceCreditNoteApplication(DEBT_REDUCTION, null, ars("0.00"), List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceCreditNoteApplication(DEBT_REDUCTION, ars("1.00"), null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvoiceLineCredit(0, ars("1.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLineCredit(1, ars("0.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLineCredit(1, null)).isInstanceOf(NullPointerException.class);
        assertThat(creditNote(DEBT_REDUCTION, "10.00", "5.00").totalCreditAmount()).isEqualTo(ars("15.00"));
    }

    @Test
    void cancellingAfterAFreeCreditAndAReversalAbsorbsTheFreeCreditOfTheLine() {
        Invoice invoice = pending();
        invoice.allocateFullInvoicePayment(ars("80000.00"), DUE_DATE, ACTOR);
        invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "0.00", "10000.00"), DUE_DATE, ACTOR);
        invoice.reversePaymentAllocation(DUE_DATE, 0, ACTOR, "contracargo");

        invoice.applyCreditNote(creditNote(CANCELLATION, "80000.00", "0.00"), DUE_DATE, ACTOR);

        assertThat(invoice.getLines().get(0).getCreditedAmount()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getLines().get(0).getFreeCreditedAmount()).isEqualTo(ars("0.00"));
    }

    @Test
    void freeCreditOnAnUnpaidInvoiceIsRejected() {
        Invoice invoice = pending();

        assertThatThrownBy(() -> invoice.applyCreditNote(creditNote(DEBT_REDUCTION, "30000.00", "10000.00"), DUE_DATE, ACTOR))
                .isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
        assertThat(invoice.getAmounts().creditedAmount()).isEqualTo(ars("0.00"));
    }

    @Test
    void domainEventsCannotBePulledBeforeTheInvoiceIsPersisted() {
        Invoice unsaved = issuedMonthly("80000.00");

        assertThatThrownBy(unsaved::pullRecordedDomainEvents).isInstanceOf(IllegalStateException.class);
    }
}
