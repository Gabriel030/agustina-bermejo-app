package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.util.Objects;

public record LateFeeSettlement(LocalDate settledOn, LateFeeSettlementSource source) {

    public LateFeeSettlement {
        Objects.requireNonNull(settledOn, "settledOn");
        Objects.requireNonNull(source, "source");
    }
}
