package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidGradeItemWeightException extends BadRequestException {
    public InvalidGradeItemWeightException(Double weight) {
        super("La ponderación (" + weight + ") debe estar entre 0 y 1");
    }
}
