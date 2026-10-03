package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class SectionEnrollmentNotFoundException extends NotFoundException {
    public SectionEnrollmentNotFoundException(Long id) {
        super("Inscripción a comisión no encontrada: " + id);
    }
}
