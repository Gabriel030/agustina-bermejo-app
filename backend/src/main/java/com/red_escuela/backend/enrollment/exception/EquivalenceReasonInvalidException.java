package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class EquivalenceReasonInvalidException extends UnprocessableEntityException {
    public EquivalenceReasonInvalidException(int minLength, int maxLength) {
        super("El motivo debe tener entre " + minLength + " y " + maxLength + " caracteres");
    }
}
