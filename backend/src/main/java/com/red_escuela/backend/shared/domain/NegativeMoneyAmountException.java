package com.red_escuela.backend.shared.domain;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class NegativeMoneyAmountException extends BadRequestException {

    public NegativeMoneyAmountException() {
        super("Money amount must not be negative");
    }
}
