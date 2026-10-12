package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.shared.domain.Money;

import java.util.Objects;

public record CreditNoteLineAmount(int lineNumber, Money amount) {

    public CreditNoteLineAmount {
        Objects.requireNonNull(amount, "amount");
    }
}
