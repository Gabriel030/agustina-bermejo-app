package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class OrganismReferenceInvalidException extends BadRequestException {

    public OrganismReferenceInvalidException() {
        super("La referencia o expediente del organismo es obligatoria (hasta 200 caracteres)");
    }
}
