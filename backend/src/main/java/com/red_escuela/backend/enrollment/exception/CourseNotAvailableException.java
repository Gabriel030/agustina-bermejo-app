package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CourseNotAvailableException extends ConflictException {
    public CourseNotAvailableException(Long courseId) {
        super("El course " + courseId + " no está disponible en el catálogo para solicitar inscripción");
    }
}
