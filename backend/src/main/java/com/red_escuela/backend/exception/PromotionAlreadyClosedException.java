package com.red_escuela.backend.exception;

public class PromotionAlreadyClosedException extends RuntimeException {
    public PromotionAlreadyClosedException(Long id) {
        super("Promotion " + id + " is already closed");
    }
}
