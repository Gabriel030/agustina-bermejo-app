package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ExamBoardRegistrationNotFoundException extends NotFoundException {
    public ExamBoardRegistrationNotFoundException(Long id) {
        super("Inscripción a mesa de examen no encontrada: " + id);
    }
}
