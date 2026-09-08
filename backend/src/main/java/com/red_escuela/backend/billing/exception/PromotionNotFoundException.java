package com.red_escuela.backend.billing.exception;

public class PromotionNotFoundException extends RuntimeException {
    public PromotionNotFoundException(Long id) {
        super("Promotion " + id + " not found");
    }
}
