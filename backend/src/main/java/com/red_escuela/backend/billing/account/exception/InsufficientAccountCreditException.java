package com.red_escuela.backend.billing.account.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InsufficientAccountCreditException extends ConflictException {

    public InsufficientAccountCreditException() {
        super("The student current account does not have enough unallocated credit");
    }
}
