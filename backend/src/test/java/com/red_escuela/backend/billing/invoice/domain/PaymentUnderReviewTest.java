package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidEvent;
import com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException;
import com.red_escuela.backend.billing.invoice.exception.PartialPaymentNotAllowedException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-153")
@Tag("D-154")
@Tag("D-136")
class PaymentUnderReviewTest {

    @Test
    void aPendingInvoiceGoesUnderReviewAndOverdueIsRejectedWhileItStaysThere() {
        Invoice invoice = issuedMonthly("80000.00");

        invoice.markPaymentUnderReview(DUE_DATE, ACTOR, "comprobante 123");

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);
        assertThat(invoice.getStatusChanges().get(invoice.getStatusChanges().size() - 1).getReason()).isEqualTo("comprobante 123");
        assertThatThrownBy(() -> invoice.markInvoiceAsOverdue(DUE_DATE.plusDays(1), ACTOR)).isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);
    }

    @Test
    void anInvoiceUnderReviewIsPaidByTheFullAllocation() {
        Invoice invoice = withId(issuedMonthly("80000.00"), 4L);
        invoice.markPaymentUnderReview(DUE_DATE, ACTOR, "comprobante");
        invoice.pullRecordedDomainEvents();

        invoice.allocateFullInvoicePayment(ars("80000.00"), DUE_DATE, ACTOR);

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(invoice.getAmounts().paidAmount()).isEqualTo(ars("80000.00"));
        assertThat(invoice.getAmounts().outstandingAmount()).isEqualTo(ars("0.00"));
        assertThat(invoice.pullRecordedDomainEvents()).containsExactly(new InvoicePaidEvent(4L, 1L, InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED, DUE_DATE));
    }

    @Test
    void aPartialAllocationIsRejectedAndNothingChanges() {
        Invoice invoice = issuedMonthly("80000.00");
        int rows = invoice.getStatusChanges().size();

        assertThatThrownBy(() -> invoice.allocateFullInvoicePayment(ars("30000.00"), DUE_DATE, ACTOR)).isInstanceOf(PartialPaymentNotAllowedException.class);

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(invoice.getAmounts().paidAmount()).isEqualTo(ars("0.00"));
        assertThat(invoice.getStatusChanges()).hasSize(rows);
    }

    @Test
    void anOverdueInvoiceCannotGoUnderReview() {
        Invoice invoice = issuedMonthly("80000.00");
        invoice.markInvoiceAsOverdue(DUE_DATE.plusDays(1), ACTOR);

        assertThatThrownBy(() -> invoice.markPaymentUnderReview(DUE_DATE.plusDays(1), ACTOR, "x")).isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
    }

    @Test
    void aPaidInvoiceCannotBeAllocatedTwice() {
        Invoice invoice = issuedMonthly("80000.00");
        invoice.allocateFullInvoicePayment(ars("80000.00"), DUE_DATE, ACTOR);

        assertThatThrownBy(() -> invoice.allocateFullInvoicePayment(ars("80000.00"), DUE_DATE, ACTOR)).isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
    }

    @Test
    void anInconsistentStatusAndAmountsCombinationIsAProgrammingError() {
        Invoice invoice = issuedMonthly("80000.00");

        assertThatThrownBy(() -> invoice.changePaymentStatus(InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED, invoice.getAmounts(), DUE_DATE, ACTOR, null))
                .isInstanceOf(com.red_escuela.backend.billing.invoice.exception.InvoiceStatusInconsistentWithAmountsException.class);
        assertThatThrownBy(() -> invoice.changePaymentStatus(InvoicePaymentEvent.DUE_DATE_PASSED, invoice.getAmounts().withFullPaymentAllocated(ars("80000.00")), DUE_DATE, ACTOR, null))
                .isInstanceOf(com.red_escuela.backend.billing.invoice.exception.InvoiceStatusInconsistentWithAmountsException.class);
        assertThatThrownBy(() -> invoice.changePaymentStatus(InvoicePaymentEvent.CANCELLATION_CREDIT_NOTE_APPLIED, invoice.getAmounts().withCreditNoteOffset(ars("30000.00")), DUE_DATE, ACTOR, null))
                .isInstanceOf(com.red_escuela.backend.billing.invoice.exception.InvoiceStatusInconsistentWithAmountsException.class);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
    }
}
