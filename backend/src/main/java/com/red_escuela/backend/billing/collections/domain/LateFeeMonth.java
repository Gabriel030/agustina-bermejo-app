package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.util.Objects;

public record LateFeeMonth(int ordinal, LocalDate factDate) {

    public LateFeeMonth {
        if (ordinal < 1) {
            throw new IllegalArgumentException("The late fee month ordinal starts at 1");
        }
        Objects.requireNonNull(factDate, "factDate");
    }
}
