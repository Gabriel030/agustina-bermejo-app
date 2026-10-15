package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class AlreadyRegisteredInExamBoardException extends ConflictException {
    public AlreadyRegisteredInExamBoardException(Long examBoardId, Long enrollmentId) {
        super("Ya existe una inscripción activa a la mesa " + examBoardId + " para la cursada " + enrollmentId);
    }
}
