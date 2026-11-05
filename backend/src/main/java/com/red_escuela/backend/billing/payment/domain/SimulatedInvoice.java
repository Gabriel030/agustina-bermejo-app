package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.account.domain.OpenInvoiceView;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

final class SimulatedInvoice {
    private final Long invoiceId;
    private final Invoice source;
    private final InvoiceDocumentType documentType;
    private final Long originInvoiceId;
    private final Long studentCurrentAccountId;
    private final LocalDate dueDate;
    private final LocalDate issueDate;
    private final boolean persisted;
    private final boolean allocatable;
    private InvoicePaymentStatus status;
    private NonNegativeMoney outstanding;

    private SimulatedInvoice(Long invoiceId, Invoice source, InvoiceDocumentType documentType, Long originInvoiceId, Long studentCurrentAccountId, LocalDate dueDate,
                             LocalDate issueDate, boolean persisted, boolean allocatable, InvoicePaymentStatus status, NonNegativeMoney outstanding) {
        this.invoiceId = invoiceId;
        this.source = source;
        this.documentType = documentType;
        this.originInvoiceId = originInvoiceId;
        this.studentCurrentAccountId = studentCurrentAccountId;
        this.dueDate = dueDate;
        this.issueDate = issueDate;
        this.persisted = persisted;
        this.allocatable = allocatable;
        this.status = status;
        this.outstanding = outstanding;
    }

    static SimulatedInvoice copyOf(Invoice invoice, boolean allocatable) {
        return new SimulatedInvoice(invoice.getId(), invoice, invoice.getDocumentType(), invoice.getOriginInvoiceId(), invoice.getStudentCurrentAccountId(),
                invoice.getDueDate(), invoice.getIssueDate(), true, allocatable, invoice.getPaymentStatus(), invoice.getAmounts().outstandingAmount());
    }

    static SimulatedInvoice lateFeeDebitNote(Long simulatedId, Long originInvoiceId, Long studentCurrentAccountId, LocalDate dueDate, LocalDate issueDate,
                                             NonNegativeMoney amount) {
        return new SimulatedInvoice(simulatedId, null, InvoiceDocumentType.DEBIT_NOTE, originInvoiceId, studentCurrentAccountId, dueDate, issueDate, false, true,
                InvoicePaymentStatus.PENDING, amount);
    }

    Long invoiceId() {
        return invoiceId;
    }

    Invoice source() {
        return source;
    }

    boolean persisted() {
        return persisted;
    }

    InvoicePaymentStatus status() {
        return status;
    }

    NonNegativeMoney outstanding() {
        return outstanding;
    }

    boolean isOpenForAllocation() {
        return allocatable && (status == InvoicePaymentStatus.PENDING || status == InvoicePaymentStatus.OVERDUE) && !outstanding.value().isZero();
    }

    OpenInvoiceView asOpenInvoiceView() {
        return new OpenInvoiceView(invoiceId, studentCurrentAccountId, documentType, originInvoiceId, dueDate, issueDate, outstanding);
    }

    void reopen(InvoicePaymentStatus statusAfter, NonNegativeMoney outstandingAfter) {
        this.status = statusAfter;
        this.outstanding = outstandingAfter;
    }

    void settle() {
        this.status = InvoicePaymentStatus.PAID;
        this.outstanding = NonNegativeMoney.zero(outstanding.value().currency());
    }
}
