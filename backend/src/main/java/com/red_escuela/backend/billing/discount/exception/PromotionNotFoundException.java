package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class PromotionNotFoundException extends NotFoundException {
    public PromotionNotFoundException(Long id) {
        super("Promotion " + id + " not found");
    }
}
