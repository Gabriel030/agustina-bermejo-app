package com.red_escuela.backend.exception;

public class UpfrontWindowClosedException extends RuntimeException {
    public UpfrontWindowClosedException(Long paymentPlanOptionId) {
        super("Upfront window is closed or disabled for payment plan option: " + paymentPlanOptionId);
    }
}
