package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.domain.event.InvoiceMarkedAsOverdueEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaymentReviewReleasedEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaymentReversedEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-154")
@Tag("D-094")
@Tag("D-153")
class InvoiceDueDateBoundaryTest {

    private static Invoice underReview(LocalDate dueDate) {
        Invoice invoice = withId(monthlyDraft("80000.00"), 8L);
        issue(invoice, dueDate.minusDays(10), dueDate);
        invoice.markPaymentUnderReview(dueDate, ACTOR, "comprobante");
        invoice.pullRecordedDomainEvents();
        return invoice;
    }

    private static Invoice paid(LocalDate dueDate) {
        Invoice invoice = withId(monthlyDraft("80000.00"), 8L);
        issue(invoice, dueDate.minusDays(10), dueDate);
        invoice.allocateFullInvoicePayment(ars("80000.00"), dueDate, ACTOR);
        invoice.pullRecordedDomainEvents();
        return invoice;
    }

    @Test
    void rejectingTheReceiptOnALeapDayDueDateGoesBackToPendingAndTheDayAfterToOverdue() {
        LocalDate due = LocalDate.of(2028, 2, 29);
        Invoice onTime = underReview(due);
        Invoice late = underReview(due);

        onTime.releasePaymentReview(due, 0, ACTOR, "rechazado");
        late.releasePaymentReview(LocalDate.of(2028, 3, 1), 0, ACTOR, "rechazado");

        assertThat(onTime.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(onTime.pullRecordedDomainEvents()).containsExactly(new InvoicePaymentReviewReleasedEvent(8L, due));
        assertThat(late.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(late.pullRecordedDomainEvents()).containsExactly(new InvoicePaymentReviewReleasedEvent(8L, LocalDate.of(2028, 3, 1)),
                new InvoiceMarkedAsOverdueEvent(8L, due, LocalDate.of(2028, 3, 1)));
    }

    @Test
    void theGraceDaysExtendTheLastDayToReverseWithoutBecomingOverdue() {
        LocalDate due = LocalDate.of(2027, 3, 31);
        Invoice inGrace = paid(due);
        Invoice afterGrace = paid(due);

        inGrace.reversePaymentAllocation(LocalDate.of(2027, 4, 3), 3, ACTOR, "reversion");
        afterGrace.reversePaymentAllocation(LocalDate.of(2027, 4, 4), 3, ACTOR, "reversion");

        assertThat(inGrace.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(afterGrace.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(inGrace.pullRecordedDomainEvents()).containsExactly(new InvoicePaymentReversedEvent(8L, InvoicePaymentStatus.PENDING));
        assertThat(afterGrace.pullRecordedDomainEvents()).containsExactly(
                new InvoicePaymentReversedEvent(8L, InvoicePaymentStatus.OVERDUE),
                new InvoiceMarkedAsOverdueEvent(8L, due, LocalDate.of(2027, 4, 4)));
    }

    @Test
    void reversingAPaidInvoiceAfterItsDueDateLeavesItOverdueWithNothingPaid() {
        LocalDate due = LocalDate.of(2027, 3, 10);
        Invoice invoice = paid(due);

        invoice.reversePaymentAllocation(LocalDate.of(2027, 3, 20), 0, ACTOR, "contracargo");

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(invoice.getAmounts().paidAmount()).isEqualTo(ars("0.00"));
        assertThat(invoice.getAmounts().outstandingAmount()).isEqualTo(ars("80000.00"));
        InvoiceStatusChange last = invoice.getStatusChanges().get(invoice.getStatusChanges().size() - 1);
        assertThat(last.getReason()).isEqualTo("contracargo");
        assertThat(last.getBusinessDate()).isEqualTo(LocalDate.of(2027, 3, 20));
    }

    @Test
    void aNegativeGraceIsAProgrammingError() {
        Invoice invoice = underReview(DUE_DATE);

        assertThatThrownBy(() -> invoice.releasePaymentReview(DUE_DATE, -1, ACTOR, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paid(DUE_DATE).reversePaymentAllocation(DUE_DATE, -1, ACTOR, "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anInvoiceUnderReviewIsNotMarkedOverdueByTheDueDate() {
        Invoice invoice = underReview(DUE_DATE);

        assertThatThrownBy(() -> invoice.markInvoiceAsOverdue(DUE_DATE.plusDays(30), ACTOR))
                .isInstanceOf(com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException.class);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);
    }

    @Test
    void anOverdueInvoiceSubmittedAfterTheDueDateIsNotProtected() {
        Invoice invoice = withId(issuedMonthly("80000.00"), 8L);
        invoice.markInvoiceAsOverdue(DUE_DATE.plusDays(1), ACTOR);

        assertThatThrownBy(() -> invoice.markPaymentUnderReview(DUE_DATE.plusDays(2), ACTOR, "tarde"))
                .isInstanceOf(com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException.class);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
    }

    @Test
    void releasingAReviewOnAnUnissuedInvoiceIsRejected() {
        Invoice draft = monthlyDraft("80000.00");

        assertThatThrownBy(() -> draft.releasePaymentReview(DUE_DATE, 0, ACTOR, "x"))
                .isInstanceOf(com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException.class);
    }

    @Test
    void markingOverdueRecordsTheOriginalDueDate() {
        Invoice invoice = withId(issuedMonthly("80000.00"), 8L);
        invoice.pullRecordedDomainEvents();

        invoice.markInvoiceAsOverdue(DUE_DATE.plusDays(1), ACTOR);

        assertThat(invoice.pullRecordedDomainEvents()).containsExactly(new InvoiceMarkedAsOverdueEvent(8L, DUE_DATE, DUE_DATE.plusDays(1)));
    }

    @Test
    void paymentOperationsNeedTheirContext() {
        Invoice invoice = issuedMonthly("80000.00");

        assertThatThrownBy(() -> invoice.markPaymentUnderReview(null, ACTOR, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> invoice.markPaymentUnderReview(DUE_DATE, null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> invoice.allocateFullInvoicePayment(null, DUE_DATE, ACTOR)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> invoice.applyCreditNote(null, DUE_DATE, ACTOR)).isInstanceOf(NullPointerException.class);
    }
}
