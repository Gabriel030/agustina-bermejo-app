package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;

public record LateFeeCorrectionContext(Long debitNoteId, InvoiceDocumentType documentType, InvoiceType invoiceType, InvoiceDocumentStatus documentStatus,
                                       InvoicePaymentStatus paymentStatus) {
}
