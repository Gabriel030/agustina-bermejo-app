package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalDateTime;

public class InvalidRegistrationWindowException extends BadRequestException {
    public InvalidRegistrationWindowException(LocalDateTime start, LocalDateTime end) {
        super("El cierre de inscripción (" + end + ") no puede ser anterior a la apertura (" + start + ")");
    }
}
