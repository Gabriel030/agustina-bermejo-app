package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalDate;

public class ExamDateOnHolidayException extends BadRequestException {
    public ExamDateOnHolidayException(LocalDate date) {
        super("La fecha de la mesa (" + date + ") coincide con un feriado");
    }
}
