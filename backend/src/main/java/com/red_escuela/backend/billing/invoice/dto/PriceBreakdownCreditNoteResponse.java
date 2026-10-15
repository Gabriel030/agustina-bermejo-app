package com.red_escuela.backend.billing.invoice.dto;

import com.red_escuela.backend.shared.domain.Money;

public record PriceBreakdownCreditNoteResponse(Long creditNoteId, String creditNoteNumber, Money amount, String description) {
}
