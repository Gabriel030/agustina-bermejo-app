package com.red_escuela.backend.billing.note.dto;

import com.red_escuela.backend.billing.note.domain.CreditNoteLine;
import com.red_escuela.backend.shared.domain.Money;

public record CreditNoteLineResponse(int invoiceLineNumber, Money amount, Money offsetPart, Money freePart) {

    public static CreditNoteLineResponse from(CreditNoteLine line) {
        return new CreditNoteLineResponse(line.getInvoiceLineNumber(), line.getAmount().value(), line.getOffsetPart().value(), line.getFreePart().value());
    }
}
