package com.red_escuela.backend.exception;

// Compartida por FeeSchedule (exactamente uno de programId/courseId) y Promotion
// (al menos uno) — mismo tipo de validación de forma, mensaje distinto según caller.
public class InvalidPricingTargetException extends RuntimeException {
    public InvalidPricingTargetException(String message) {
        super(message);
    }
}
