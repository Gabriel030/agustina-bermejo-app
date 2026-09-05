package com.red_escuela.backend.exception;

public class PromotionNotFoundException extends RuntimeException {
    public PromotionNotFoundException(Long id) {
        super("Promotion " + id + " not found");
    }
}
