package com.red_escuela.backend.billing.pricing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class PriceNotFoundException extends NotFoundException {
    public PriceNotFoundException(Long priceId) {
        super("No existe el precio " + priceId + ".");
    }
}
