package com.red_escuela.backend.billing.annualupfront.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class AnnualUpfrontTermsNotAcceptedException extends BadRequestException {
    public AnnualUpfrontTermsNotAcceptedException() {
        super("Para pedir el pago anual hay que aceptar la condición vigente");
    }
}
