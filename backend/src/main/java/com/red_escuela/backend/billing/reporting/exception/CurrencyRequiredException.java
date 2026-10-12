package com.red_escuela.backend.billing.reporting.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class CurrencyRequiredException extends BadRequestException {

    public CurrencyRequiredException() {
        super("Hay cuentas en más de una moneda: hay que elegir la moneda del tablero");
    }
}
