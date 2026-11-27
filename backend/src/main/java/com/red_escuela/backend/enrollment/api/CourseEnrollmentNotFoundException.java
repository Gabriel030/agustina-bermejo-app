package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class CourseEnrollmentNotFoundException extends NotFoundException {
    public CourseEnrollmentNotFoundException(Long id) {
        super("Matriculación a curso no encontrada: " + id);
    }
}
