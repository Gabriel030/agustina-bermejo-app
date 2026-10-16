package com.red_escuela.backend.billing.pricing.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class PriceFilterConflictException extends BadRequestException {
    public PriceFilterConflictException() {
        super("Los precios se filtran por carrera o por curso, no por ambos.");
    }
}
