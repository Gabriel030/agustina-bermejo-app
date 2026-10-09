package com.red_escuela.backend.billing.note.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record CreditNoteLineAmount(int lineNumber, BigDecimal amount) {

    public CreditNoteLineAmount {
        Objects.requireNonNull(amount, "amount");
    }
}
