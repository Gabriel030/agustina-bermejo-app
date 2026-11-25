package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class StudentEmailChangeNotAvailableException extends UnprocessableEntityException {
    public StudentEmailChangeNotAvailableException(Long studentId) {
        super("El cambio de email del Student " + studentId + " todavía no está disponible");
    }
}
