package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class StudentFreeByAbsencesException extends ConflictException {
    public StudentFreeByAbsencesException(Long enrollmentId) {
        super("La inscripción " + enrollmentId + " supera el máximo de inasistencias: no puede inscribirse al final hasta justificarlas");
    }
}
