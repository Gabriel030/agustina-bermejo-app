package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class NoDebtToExceptException extends ConflictException {

    public NoDebtToExceptException() {
        super("El alumno no tiene deuda: no hace falta una excepción");
    }
}
