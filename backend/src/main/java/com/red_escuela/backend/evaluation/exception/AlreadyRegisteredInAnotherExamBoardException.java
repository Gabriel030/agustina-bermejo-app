package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class AlreadyRegisteredInAnotherExamBoardException extends ConflictException {
    public AlreadyRegisteredInAnotherExamBoardException(Long subjectId, Long otroExamBoardId) {
        super("Ya hay una inscripción activa a otro llamado (mesa " + otroExamBoardId
                + ") de la materia " + subjectId + " — hay que darse de baja de esa antes de anotarse a otra");
    }
}
