package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class InvalidScoreException extends UnprocessableEntityException {
    public InvalidScoreException(String detail) {
        super("La nota de origen es inválida: " + detail);
    }
}
