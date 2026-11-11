package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ClosureStudentNotFoundException extends NotFoundException {
    public ClosureStudentNotFoundException(Long enrollmentId) {
        super("La inscripción " + enrollmentId + " no figura en el cierre de la comisión");
    }
}
