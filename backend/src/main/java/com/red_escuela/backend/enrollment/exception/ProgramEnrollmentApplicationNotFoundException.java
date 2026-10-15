package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ProgramEnrollmentApplicationNotFoundException extends NotFoundException {
    public ProgramEnrollmentApplicationNotFoundException(Long id) {
        super("Solicitud de alta en carrera no encontrada: " + id);
    }
}
