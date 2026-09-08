package com.red_escuela.backend.billing.exception;

public class PriceRevisionNotFoundException extends RuntimeException {
    public PriceRevisionNotFoundException(String message) {
        super(message);
    }
}
