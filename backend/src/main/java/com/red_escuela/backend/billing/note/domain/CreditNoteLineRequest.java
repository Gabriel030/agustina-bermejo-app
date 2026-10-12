package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.Objects;

public record CreditNoteLineRequest(int lineNumber, NonNegativeMoney amount) {

    public CreditNoteLineRequest {
        Objects.requireNonNull(amount, "amount");
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be at least 1");
        }
        if (amount.value().isZero()) {
            throw new IllegalArgumentException("A credit note line must be greater than zero");
        }
    }
}
