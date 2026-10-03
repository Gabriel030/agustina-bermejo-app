package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class PriceRevisionNotFoundException extends NotFoundException {
    public PriceRevisionNotFoundException(String message) {
        super(message);
    }
}
