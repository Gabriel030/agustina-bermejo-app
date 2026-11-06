package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class NotRegularException extends ConflictException {

    public NotRegularException() {
        super("El alumno no cumple las condiciones de alumno regular");
    }
}
