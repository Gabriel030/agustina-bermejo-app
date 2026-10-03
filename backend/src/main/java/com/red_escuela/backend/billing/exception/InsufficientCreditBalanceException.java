package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.math.BigDecimal;

public class InsufficientCreditBalanceException extends BadRequestException {
    public InsufficientCreditBalanceException(Long studentId, BigDecimal requested, BigDecimal available) {
        super("Student " + studentId + " requested credit of " + requested + " but only has " + available + " available");
    }
}
