package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ExceptionTargetMismatchException extends ConflictException {

    public ExceptionTargetMismatchException() {
        super("La excepción no corresponde a este alumno o inscripción");
    }
}
