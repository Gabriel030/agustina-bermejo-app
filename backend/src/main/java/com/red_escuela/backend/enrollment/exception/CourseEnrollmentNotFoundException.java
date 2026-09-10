package com.red_escuela.backend.enrollment.exception;

public class CourseEnrollmentNotFoundException extends RuntimeException {
    public CourseEnrollmentNotFoundException(Long id) {
        super("Matriculación a curso no encontrada: " + id);
    }
}
