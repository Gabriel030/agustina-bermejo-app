package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class DniRequiredException extends BadRequestException {
    public DniRequiredException(Long personId) {
        super("La persona " + personId + " no tiene DNI cargado");
    }
}
