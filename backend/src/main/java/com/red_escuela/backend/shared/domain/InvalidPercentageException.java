package com.red_escuela.backend.shared.domain;

import java.math.BigDecimal;

public class InvalidPercentageException extends IllegalArgumentException {

    public InvalidPercentageException(BigDecimal fraction) {
        super("Percentage fraction must be between 0 and 1, got " + fraction.toPlainString());
    }
}
