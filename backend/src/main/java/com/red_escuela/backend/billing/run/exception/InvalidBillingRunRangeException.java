package com.red_escuela.backend.billing.run.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidBillingRunRangeException extends BadRequestException {

    public InvalidBillingRunRangeException() {
        super("The 'from' date must not be after the 'to' date");
    }
}
