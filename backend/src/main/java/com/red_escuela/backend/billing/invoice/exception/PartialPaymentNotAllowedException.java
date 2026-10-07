package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PartialPaymentNotAllowedException extends ConflictException {

    public PartialPaymentNotAllowedException() {
        super("The allocated amount must cover the whole outstanding amount");
    }
}
