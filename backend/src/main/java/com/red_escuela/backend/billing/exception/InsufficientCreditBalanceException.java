package com.red_escuela.backend.billing.exception;

import java.math.BigDecimal;

public class InsufficientCreditBalanceException extends RuntimeException {
    public InsufficientCreditBalanceException(Long studentId, BigDecimal requested, BigDecimal available) {
        super("Student " + studentId + " requested credit of " + requested + " but only has " + available + " available");
    }
}
