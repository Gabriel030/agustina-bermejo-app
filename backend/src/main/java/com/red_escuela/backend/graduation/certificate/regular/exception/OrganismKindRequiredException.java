package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class OrganismKindRequiredException extends BadRequestException {

    public OrganismKindRequiredException() {
        super("El tipo de organismo es obligatorio");
    }
}
