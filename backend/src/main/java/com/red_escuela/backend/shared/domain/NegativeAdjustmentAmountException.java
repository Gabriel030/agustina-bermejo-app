package com.red_escuela.backend.shared.domain;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class NegativeAdjustmentAmountException extends BadRequestException {

    public NegativeAdjustmentAmountException() {
        super("Nominal adjustment amount must not be negative");
    }
}
