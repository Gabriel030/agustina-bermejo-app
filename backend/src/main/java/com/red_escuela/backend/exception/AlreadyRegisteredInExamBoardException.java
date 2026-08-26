package com.red_escuela.backend.exception;

public class AlreadyRegisteredInExamBoardException extends RuntimeException {
    public AlreadyRegisteredInExamBoardException(Long examBoardId, Long enrollmentId) {
        super("Ya existe una inscripción activa a la mesa " + examBoardId + " para la cursada " + enrollmentId);
    }
}
