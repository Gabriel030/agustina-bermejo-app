package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.shared.domain.Money;

import java.util.Objects;

public record WithdrawalLineAmount(int lineNumber, Money amount) {

    public WithdrawalLineAmount {
        Objects.requireNonNull(amount, "amount");
    }
}
