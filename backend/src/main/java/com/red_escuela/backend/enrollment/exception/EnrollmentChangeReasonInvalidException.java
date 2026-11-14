package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class EnrollmentChangeReasonInvalidException extends BadRequestException {
    public EnrollmentChangeReasonInvalidException(int minLength, int maxLength) {
        super("El motivo debe tener entre " + minLength + " y " + maxLength + " caracteres");
    }
}
