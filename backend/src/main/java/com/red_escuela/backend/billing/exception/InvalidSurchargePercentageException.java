package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.math.BigDecimal;

public class InvalidSurchargePercentageException extends BadRequestException {
    public InvalidSurchargePercentageException(BigDecimal percentage) {
        super("Surcharge percentage must be between 0 and 1, got " + percentage);
    }
}
