package com.red_escuela.backend.shared.domain;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.math.BigDecimal;

public class InvalidPercentageException extends BadRequestException {

    public InvalidPercentageException(BigDecimal fraction) {
        super("Percentage fraction must be between 0 and 1, got " + fraction.toPlainString());
    }
}
