package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class DiscountDefinitionNotFoundException extends NotFoundException {
    public DiscountDefinitionNotFoundException(Long discountDefinitionId) {
        super("No existe la promoción " + discountDefinitionId + ".");
    }
}
