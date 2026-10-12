package com.red_escuela.backend.billing.invoice.domain.event;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public record InvoiceIssuedEvent(Long invoiceId, Long studentCurrentAccountId, InvoiceDocumentType documentType,
                                 InvoiceType invoiceType, NonNegativeMoney total, LocalDate issueDate, LocalDate dueDate) {
}
