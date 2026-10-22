package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PromotionAlreadyClosedException extends ConflictException {
    public PromotionAlreadyClosedException(Long id) {
        super("Promotion " + id + " is already closed");
    }
}
