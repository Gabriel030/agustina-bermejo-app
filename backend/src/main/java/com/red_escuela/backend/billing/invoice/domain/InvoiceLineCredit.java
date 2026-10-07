package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.Objects;

public record InvoiceLineCredit(int lineNumber, NonNegativeMoney amount) {

    public InvoiceLineCredit {
        Objects.requireNonNull(amount, "amount");
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be at least 1");
        }
        if (amount.value().isZero()) {
            throw new IllegalArgumentException("A line credit must be greater than zero");
        }
    }
}
