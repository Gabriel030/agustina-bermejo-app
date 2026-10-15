package com.red_escuela.backend.shared.domain;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.math.BigDecimal;

public class InvalidGradeException extends BadRequestException {

    public InvalidGradeException(BigDecimal value) {
        super("Grade must be between 1 and 10 with at most 2 decimals, got " + value.toPlainString());
    }
}
