package com.red_escuela.backend.exception;

public class ExamBoardNotFoundException extends RuntimeException {
    public ExamBoardNotFoundException(Long id) {
        super("Mesa de examen no encontrada: " + id);
    }
}
