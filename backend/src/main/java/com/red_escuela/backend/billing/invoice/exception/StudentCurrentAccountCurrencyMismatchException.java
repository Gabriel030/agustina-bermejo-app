package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class StudentCurrentAccountCurrencyMismatchException extends BadRequestException {

    public StudentCurrentAccountCurrencyMismatchException() {
        super("The currency does not match the currency of the student current account");
    }
}
