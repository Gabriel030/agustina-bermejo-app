package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;

public record ReceiptInvoiceFact(Long invoiceId, Long studentCurrentAccountId, Currency currency, boolean issued, InvoicePaymentStatus paymentStatus,
                                 LocalDate dueDate, Money outstanding, boolean programAdmission) {

    public static final Comparator<ReceiptInvoiceFact> OLDEST_DUE_FIRST = Comparator
            .comparing(ReceiptInvoiceFact::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ReceiptInvoiceFact::invoiceId);

    public ReceiptInvoiceFact {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        Objects.requireNonNull(currency, "currency");
    }

    public ReceiptInvoiceFact(Long invoiceId, Long studentCurrentAccountId, Currency currency, boolean issued, InvoicePaymentStatus paymentStatus,
                              LocalDate dueDate, Money outstanding) {
        this(invoiceId, studentCurrentAccountId, currency, issued, paymentStatus, dueDate, outstanding, false);
    }

    public boolean isUnderReview() {
        return issued && paymentStatus == InvoicePaymentStatus.PAYMENT_UNDER_REVIEW;
    }

    public boolean isOpenToReceipt() {
        return issued && (paymentStatus == InvoicePaymentStatus.PENDING || paymentStatus == InvoicePaymentStatus.OVERDUE);
    }
}
