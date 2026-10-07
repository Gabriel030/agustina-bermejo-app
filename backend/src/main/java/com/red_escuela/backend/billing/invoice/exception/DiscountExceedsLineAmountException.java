package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class DiscountExceedsLineAmountException extends ConflictException {

    public DiscountExceedsLineAmountException() {
        super("The discount exceeds the amount of the line");
    }
}
