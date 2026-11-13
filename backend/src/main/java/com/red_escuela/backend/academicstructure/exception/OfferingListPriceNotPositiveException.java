package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class OfferingListPriceNotPositiveException extends BadRequestException {
    public OfferingListPriceNotPositiveException(String concept) {
        super("El precio de " + concept + " es obligatorio y debe ser mayor que cero. Para ofrecerlo gratis, usá un descuento.");
    }
}
