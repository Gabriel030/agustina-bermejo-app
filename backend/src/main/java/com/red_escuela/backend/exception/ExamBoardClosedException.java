package com.red_escuela.backend.exception;

// Cerrar el acta de una mesa (ExamBoard.status = CLOSED) es un punto sin retorno operativo:
// bloquea cualquier alta/edición de GradeItem asociado a esa mesa.
public class ExamBoardClosedException extends RuntimeException {
    public ExamBoardClosedException(Long examBoardId) {
        super("La mesa de examen " + examBoardId + " ya tiene el acta cerrada");
    }
}
