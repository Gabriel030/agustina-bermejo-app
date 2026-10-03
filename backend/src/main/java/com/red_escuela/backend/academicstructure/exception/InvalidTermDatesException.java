package com.red_escuela.backend.academicstructure.exception;

import java.time.LocalDateTime;

public class InvalidTermDatesException extends RuntimeException {
    public InvalidTermDatesException(LocalDateTime start, LocalDateTime end) {
        super("El período no puede terminar (" + end + ") antes de empezar (" + start + ")");
    }
}
