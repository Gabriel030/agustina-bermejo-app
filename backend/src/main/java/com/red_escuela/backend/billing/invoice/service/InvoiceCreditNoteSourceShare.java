package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.shared.domain.Money;

public record InvoiceCreditNoteSourceShare(Long creditNoteId, String creditNoteNumber, Money amountApplied) {
}
