package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import com.red_escuela.backend.shared.domain.Money;

public class InsufficientCreditBalanceException extends BadRequestException {
    public InsufficientCreditBalanceException(Long studentId, Money requested, Money available) {
        super("Student " + studentId + " requested credit of " + requested.amount() + " " + requested.currency() + " but only has " + available.amount() + " " + available.currency() + " available");
    }
}
