package com.red_escuela.backend.evaluation.exception;

import java.time.LocalDate;

public class ExamDateOnHolidayException extends RuntimeException {
    public ExamDateOnHolidayException(LocalDate date) {
        super("La fecha de la mesa (" + date + ") coincide con un feriado");
    }
}
