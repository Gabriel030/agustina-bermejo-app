package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.Objects;

public record OpenInvoiceView(Long invoiceId, Long studentCurrentAccountId, InvoiceDocumentType documentType, Long originInvoiceId,
                              LocalDate dueDate, LocalDate issueDate, NonNegativeMoney outstandingAmount) {

    public OpenInvoiceView {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(outstandingAmount, "outstandingAmount");
    }

    public static OpenInvoiceView of(Invoice invoice) {
        return new OpenInvoiceView(invoice.getId(), invoice.getStudentCurrentAccountId(), invoice.getDocumentType(), invoice.getOriginInvoiceId(),
                invoice.getDueDate(), invoice.getIssueDate(), invoice.getAmounts().outstandingAmount());
    }

    public Currency currency() {
        return outstandingAmount.value().currency();
    }
}
