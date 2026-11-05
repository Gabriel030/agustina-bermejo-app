package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ReceiptInvoiceFacts {
    public static final Long ACCOUNT = 7L;

    private ReceiptInvoiceFacts() {
    }

    public static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    public static ReceiptInvoiceFact invoice(long id, InvoicePaymentStatus status, LocalDate dueDate, String outstanding) {
        return new ReceiptInvoiceFact(id, ACCOUNT, Currency.ARS, true, status, dueDate, ars(outstanding));
    }

    public static ReceiptInvoiceFact pending(long id, LocalDate dueDate, String outstanding) {
        return invoice(id, InvoicePaymentStatus.PENDING, dueDate, outstanding);
    }

    public static ReceiptInvoiceFact programAdmission(long id, LocalDate dueDate, String outstanding) {
        return new ReceiptInvoiceFact(id, ACCOUNT, Currency.ARS, true, InvoicePaymentStatus.PENDING, dueDate, ars(outstanding), true);
    }

    public static ReceiptInvoiceFact overdue(long id, LocalDate dueDate, String outstanding) {
        return invoice(id, InvoicePaymentStatus.OVERDUE, dueDate, outstanding);
    }

    public static ReceiptInvoiceFact underReview(long id, LocalDate dueDate, String outstanding) {
        return invoice(id, InvoicePaymentStatus.PAYMENT_UNDER_REVIEW, dueDate, outstanding);
    }
}
