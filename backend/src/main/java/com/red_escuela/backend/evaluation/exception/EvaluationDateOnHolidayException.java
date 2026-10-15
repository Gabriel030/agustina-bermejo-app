package com.red_escuela.backend.evaluation.exception;

import java.time.LocalDate;

public class EvaluationDateOnHolidayException extends InvalidEvaluationDateException {
    public EvaluationDateOnHolidayException(LocalDate date) {
        super(date, "coincide con un feriado");
    }
}
