package com.red_escuela.backend.evaluation.exception;

public class StudentFreeByAbsencesException extends RuntimeException {
    public StudentFreeByAbsencesException(Long enrollmentId) {
        super("La inscripción " + enrollmentId + " supera el máximo de inasistencias: no puede inscribirse al final hasta justificarlas");
    }
}
