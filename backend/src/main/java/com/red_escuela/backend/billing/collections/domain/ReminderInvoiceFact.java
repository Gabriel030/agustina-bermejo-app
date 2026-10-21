package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderMilestoneDates;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.Objects;

public record ReminderInvoiceFact(Long invoiceId, Long originInvoiceId, InvoiceDocumentType documentType, InvoiceType invoiceType,
                                  InvoiceDocumentStatus documentStatus, InvoicePaymentStatus paymentStatus, LocalDate issueDate,
                                  LocalDate dueDate, LocalDate suspensionDate, NonNegativeMoney balance, Long programEnrollmentId,
                                  Long courseEnrollmentId, boolean receiptUnderReview, boolean openCancellationRequest) {

    public ReminderInvoiceFact {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(invoiceType, "invoiceType");
        Objects.requireNonNull(documentStatus, "documentStatus");
        Objects.requireNonNull(paymentStatus, "paymentStatus");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(balance, "balance");
    }

    public Currency currency() {
        return balance.value().currency();
    }

    public boolean isDebitNote() {
        return documentType == InvoiceDocumentType.DEBIT_NOTE;
    }

    public ReminderMilestoneDates milestoneDates() {
        return new ReminderMilestoneDates(issueDate, dueDate, suspensionDate);
    }
}
