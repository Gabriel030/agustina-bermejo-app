package com.red_escuela.backend.evaluation.exception;

import java.time.LocalDateTime;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ExamBoardDateInPastException extends BadRequestException {
    public ExamBoardDateInPastException(LocalDateTime examDate) {
        super("La fecha de la mesa (" + examDate + ") ya pasó");
    }
}
