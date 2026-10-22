package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ReminderInvoiceFacts {

    private ReminderInvoiceFacts() {
    }

    public static ReminderInvoiceFact installment(long id, String issueDate, String dueDate) {
        return fact(id, null, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING,
                issueDate, dueDate, null, false, false);
    }

    public static ReminderInvoiceFact installmentOfAnEnrollment(long id, String issueDate, String dueDate, boolean enrollmentSuspended, boolean issuedAfterPreviousRun) {
        return new ReminderInvoiceFact(id, null, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING,
                LocalDate.parse(issueDate), LocalDate.parse(dueDate), null, NonNegativeMoney.of(new BigDecimal("80000.00"), Currency.ARS), 1L, null, false, false,
                enrollmentSuspended, issuedAfterPreviousRun);
    }

    public static ReminderInvoiceFact lateFeeDebitNote(long id, long originId, String issueDate, String dueDate) {
        return fact(id, originId, InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING,
                issueDate, dueDate, null, false, false);
    }

    public static ReminderInvoiceFact fact(long id, Long originId, InvoiceDocumentType documentType, InvoiceType invoiceType, InvoiceDocumentStatus documentStatus,
                                           InvoicePaymentStatus paymentStatus, String issueDate, String dueDate, String suspensionDate,
                                           boolean receiptUnderReview, boolean openCancellationRequest) {
        return new ReminderInvoiceFact(id, originId, documentType, invoiceType, documentStatus, paymentStatus, LocalDate.parse(issueDate), LocalDate.parse(dueDate),
                suspensionDate == null ? null : LocalDate.parse(suspensionDate), NonNegativeMoney.of(new BigDecimal("80000.00"), Currency.ARS), 1L, null,
                receiptUnderReview, openCancellationRequest);
    }
}
