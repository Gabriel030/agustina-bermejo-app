package com.red_escuela.backend.evaluation.exception;

public class InvalidGradeScoreException extends RuntimeException {
    public InvalidGradeScoreException(Double score, Double maxScore) {
        super("Nota inválida: " + score + (maxScore == null ? "" : " sobre " + maxScore));
    }
}
