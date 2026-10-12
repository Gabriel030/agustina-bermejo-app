package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalDateTime;

public class InvalidTermDatesException extends BadRequestException {
    public InvalidTermDatesException(LocalDateTime start, LocalDateTime end) {
        super("El período no puede terminar (" + end + ") antes de empezar (" + start + ")");
    }
}
