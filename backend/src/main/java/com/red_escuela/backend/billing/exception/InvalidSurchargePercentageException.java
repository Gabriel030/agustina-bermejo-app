package com.red_escuela.backend.billing.exception;

import java.math.BigDecimal;

public class InvalidSurchargePercentageException extends RuntimeException {
    public InvalidSurchargePercentageException(BigDecimal percentage) {
        super("Surcharge percentage must be between 0 and 1, got " + percentage);
    }
}
