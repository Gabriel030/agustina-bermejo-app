package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ChargebackAlreadyReversedException extends ConflictException {

    public ChargebackAlreadyReversedException() {
        super("The chargeback was already reversed");
    }
}
