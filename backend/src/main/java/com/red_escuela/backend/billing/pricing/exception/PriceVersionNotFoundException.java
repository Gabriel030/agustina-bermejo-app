package com.red_escuela.backend.billing.pricing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class PriceVersionNotFoundException extends NotFoundException {
    public PriceVersionNotFoundException(Long priceVersionId) {
        super("No existe la versión de precio " + priceVersionId + ".");
    }
}
