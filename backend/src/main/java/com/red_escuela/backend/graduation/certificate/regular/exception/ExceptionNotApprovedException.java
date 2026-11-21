package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ExceptionNotApprovedException extends ConflictException {

    public ExceptionNotApprovedException() {
        super("La excepción no está aprobada o ya venció");
    }
}
