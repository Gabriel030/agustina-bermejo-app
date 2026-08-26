package com.red_escuela.backend.exception;

// La materia de la cursada tiene que coincidir con la materia de la mesa de examen —
// mismo espíritu que InvalidSectionChangeException en Inscripciones académicas.
public class ExamBoardSubjectMismatchException extends RuntimeException {
    public ExamBoardSubjectMismatchException(Long examBoardId, Long enrollmentId) {
        super("La cursada " + enrollmentId + " no es de la misma materia que la mesa " + examBoardId);
    }
}
