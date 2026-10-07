package com.red_escuela.backend.billing.support;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
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
        return hasStatus(InvoiceStatus.PENDING);
    }

    public InvoiceAssertions isPaid() {
        return hasStatus(InvoiceStatus.PAID);
    }

    public InvoiceAssertions isOverdue() {
        return hasStatus(InvoiceStatus.OVERDUE);
    }

    public InvoiceAssertions isCancelled() {
        return hasStatus(InvoiceStatus.CANCELLED);
    }

    public InvoiceAssertions isWaived() {
        return hasStatus(InvoiceStatus.WAIVED);
    }

    public InvoiceAssertions isNotPaid() {
        isNotNull();
        assertThat(actual.getStatus()).isNotEqualTo(InvoiceStatus.PAID);
        return this;
    }

    public InvoiceAssertions hasTotalAmount(String amount) {
        isNotNull();
        assertThat(actual.getAmountDue().amount()).isEqualByComparingTo(new BigDecimal(amount));
        return this;
    }

    public InvoiceAssertions hasSingleLine(FeeType feeType, String period) {
        isNotNull();
        assertThat(actual.getFeeType()).isEqualTo(feeType);
        assertThat(actual.getPeriod()).isEqualTo(YearMonth.parse(period));
        return this;
    }

    private InvoiceAssertions hasStatus(InvoiceStatus expected) {
        isNotNull();
        assertThat(actual.getStatus()).as("invoice %s", actual.getId()).isEqualTo(expected);
        return this;
    }
}
