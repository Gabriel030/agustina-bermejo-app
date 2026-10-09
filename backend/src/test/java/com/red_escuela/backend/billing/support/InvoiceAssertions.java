package com.red_escuela.backend.billing.support;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import org.assertj.core.api.AbstractAssert;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

public final class InvoiceAssertions extends AbstractAssert<InvoiceAssertions, Invoice> {

    private InvoiceAssertions(Invoice actual) {
        super(actual, InvoiceAssertions.class);
    }

    public static InvoiceAssertions assertThatInvoice(Invoice invoice) {
        return new InvoiceAssertions(invoice);
    }

    public InvoiceAssertions isPending() {
        return hasStatus(InvoicePaymentStatus.PENDING);
    }

    public InvoiceAssertions isPaid() {
        return hasStatus(InvoicePaymentStatus.PAID);
    }

    public InvoiceAssertions isOverdue() {
        return hasStatus(InvoicePaymentStatus.OVERDUE);
    }

    public InvoiceAssertions isCancelled() {
        return hasStatus(InvoicePaymentStatus.CANCELLED);
    }

    public InvoiceAssertions isNotPaid() {
        isNotNull();
        assertThat(actual.getPaymentStatus()).isNotEqualTo(InvoicePaymentStatus.PAID);
        return this;
    }

    public InvoiceAssertions hasTotalAmount(String amount) {
        isNotNull();
        assertThat(actual.getAmounts().total().value().amount()).isEqualByComparingTo(new BigDecimal(amount));
        return this;
    }

    public InvoiceAssertions hasSingleLine(FeeType feeType, String period) {
        isNotNull();
        assertThat(actual.getLines()).hasSize(1);
        assertThat(actual.getPrimaryLine().getFeeType()).isEqualTo(feeType);
        assertThat(actual.getPrimaryLine().getPeriod()).isEqualTo(YearMonth.parse(period));
        return this;
    }

    private InvoiceAssertions hasStatus(InvoicePaymentStatus expected) {
        isNotNull();
        assertThat(actual.getPaymentStatus()).as("invoice %s", actual.getId()).isEqualTo(expected);
        return this;
    }
}
