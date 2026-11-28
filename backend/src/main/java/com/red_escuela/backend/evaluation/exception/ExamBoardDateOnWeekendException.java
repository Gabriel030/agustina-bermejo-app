package com.red_escuela.backend.evaluation.exception;

import java.time.LocalDate;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ExamBoardDateOnWeekendException extends BadRequestException {
    public ExamBoardDateOnWeekendException(LocalDate date) {
        super("La fecha de la mesa (" + date + ") cae en fin de semana");
    }
}
