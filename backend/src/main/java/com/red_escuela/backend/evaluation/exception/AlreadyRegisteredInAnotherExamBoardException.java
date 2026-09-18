package com.red_escuela.backend.evaluation.exception;

public class AlreadyRegisteredInAnotherExamBoardException extends RuntimeException {
    public AlreadyRegisteredInAnotherExamBoardException(Long subjectId, Long otroExamBoardId) {
        super("Ya hay una inscripción activa a otro llamado (mesa " + otroExamBoardId
                + ") de la materia " + subjectId + " — hay que darse de baja de esa antes de anotarse a otra");
    }
}
