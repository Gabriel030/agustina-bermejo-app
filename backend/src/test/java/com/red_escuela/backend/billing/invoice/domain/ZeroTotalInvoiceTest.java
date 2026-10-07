package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.domain.event.InvoiceIssuedEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidEvent;
import com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-150")
class ZeroTotalInvoiceTest {

    @Test
    void aFullScholarshipInvoiceIsPaidOnIssuingWithoutAllocations() {
        Invoice invoice = withId(zeroTotalDraft(), 5L);

        issue(invoice);

        assertThat(invoice.getAmounts().total()).isEqualTo(ars("0.00"));
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(invoice.getAmounts().paidAmount()).isEqualTo(ars("0.00"));
        assertThat(invoice.getStatusChanges().get(2)).satisfies(change -> {
            assertThat(change.getFromStatus()).isNull();
            assertThat(change.getToStatus()).isEqualTo("PAID");
            assertThat(change.getEvent()).isEqualTo("ISSUED_WITH_ZERO_TOTAL");
        });
        assertThat(invoice.pullRecordedDomainEvents()).hasSize(2).satisfies(events -> {
            assertThat(events.get(0)).isInstanceOf(InvoiceIssuedEvent.class);
            assertThat(events.get(1)).isEqualTo(new InvoicePaidEvent(5L, 1L, InvoicePaymentEvent.ISSUED_WITH_ZERO_TOTAL, ISSUE_DATE));
        });
    }

    @Test
    void aZeroTotalInvoiceCannotBecomeOverdue() {
        Invoice invoice = zeroTotalDraft();
        issue(invoice);

        assertThatThrownBy(() -> invoice.markInvoiceAsOverdue(DUE_DATE.plusDays(5), ACTOR)).isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
    }

    @Test
    void aZeroTotalInvoiceHasNoAllocationToReverse() {
        Invoice invoice = zeroTotalDraft();
        issue(invoice);

        assertThatThrownBy(() -> invoice.reversePaymentAllocation(DUE_DATE, 0, ACTOR, "x")).isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
        assertThatThrownBy(() -> invoice.reversePaymentAllocation(DUE_DATE.plusDays(1), 0, ACTOR, "x")).isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
    }
}
