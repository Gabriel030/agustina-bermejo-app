package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ExceptionAlreadyConsumedException extends ConflictException {

    public ExceptionAlreadyConsumedException() {
        super("La excepción ya fue usada");
    }
}
