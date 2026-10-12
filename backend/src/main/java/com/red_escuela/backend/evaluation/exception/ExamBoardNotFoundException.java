package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ExamBoardNotFoundException extends NotFoundException {
    public ExamBoardNotFoundException(Long id) {
        super("Mesa de examen no encontrada: " + id);
    }
}
