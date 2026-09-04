package com.red_escuela.backend.exception;

public class NoActivePaymentPlanException extends RuntimeException {
    public NoActivePaymentPlanException(String message) {
        super(message);
    }
}
