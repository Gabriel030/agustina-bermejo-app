package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ProgramEnrollmentNotFoundException extends NotFoundException {
    public ProgramEnrollmentNotFoundException(Long id) {
        super("Matriculación a carrera no encontrada: " + id);
    }
}
