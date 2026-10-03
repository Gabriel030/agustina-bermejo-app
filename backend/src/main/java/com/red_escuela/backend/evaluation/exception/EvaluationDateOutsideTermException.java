package com.red_escuela.backend.evaluation.exception;

import java.time.LocalDate;

public class EvaluationDateOutsideTermException extends InvalidEvaluationDateException {
    public EvaluationDateOutsideTermException(LocalDate date) {
        super(date, "fuera del rango del cuatrimestre");
    }
}
