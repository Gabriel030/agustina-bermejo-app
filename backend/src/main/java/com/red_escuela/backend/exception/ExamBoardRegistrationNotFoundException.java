package com.red_escuela.backend.exception;

public class ExamBoardRegistrationNotFoundException extends RuntimeException {
    public ExamBoardRegistrationNotFoundException(Long id) {
        super("Inscripción a mesa de examen no encontrada: " + id);
    }
}
