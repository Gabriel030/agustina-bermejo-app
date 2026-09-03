package com.red_escuela.backend.exception;

// Un alumno solo puede estar anotado a UN llamado por vez para la misma materia — pedido
// explícito del usuario. Se dispara al intentar inscribirse a una mesa nueva teniendo ya una
// inscripción REGISTERED a otra mesa de la misma materia; hay que darse de baja de esa primero.
public class AlreadyRegisteredInAnotherExamBoardException extends RuntimeException {
    public AlreadyRegisteredInAnotherExamBoardException(Long subjectId, Long otroExamBoardId) {
        super("Ya hay una inscripción activa a otro llamado (mesa " + otroExamBoardId
                + ") de la materia " + subjectId + " — hay que darse de baja de esa antes de anotarse a otra");
    }
}
