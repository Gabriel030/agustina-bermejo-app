package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalDate;

public class InvalidEvaluationDateException extends BadRequestException {
    public InvalidEvaluationDateException(LocalDate date, String reason) {
        super("Fecha inválida (" + date + "): " + reason);
    }
}
