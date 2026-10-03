package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidGradeScoreException extends BadRequestException {
    public InvalidGradeScoreException(Double score, Double maxScore) {
        super("Nota inválida: " + score + (maxScore == null ? "" : " sobre " + maxScore));
    }
}
