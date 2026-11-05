package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;

public record LateFeeDebitNoteAnnulmentContext(Long debitNoteId, String debitNoteNumber, InvoicePaymentStatus paymentStatus) {
}
