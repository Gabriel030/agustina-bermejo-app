package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class FeatureChangeReasonInvalidException extends BadRequestException {
    public FeatureChangeReasonInvalidException(int minLength, int maxLength) {
        super("El motivo es obligatorio y tiene que tener entre " + minLength + " y " + maxLength + " caracteres");
    }
}
