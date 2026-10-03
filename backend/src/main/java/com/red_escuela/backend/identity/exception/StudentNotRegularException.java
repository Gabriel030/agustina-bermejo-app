package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class StudentNotRegularException extends BadRequestException {
    public StudentNotRegularException(Long enrollmentId) {
        super("La cursada " + enrollmentId + " no está REGULAR, no puede inscribirse a una mesa de examen");
    }
}
