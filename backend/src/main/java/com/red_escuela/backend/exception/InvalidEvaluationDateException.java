package com.red_escuela.backend.exception;

import java.time.LocalDate;

public class InvalidEvaluationDateException extends RuntimeException {
    public InvalidEvaluationDateException(LocalDate date, String reason) {
        super("Fecha inválida (" + date + "): " + reason);
    }
}
