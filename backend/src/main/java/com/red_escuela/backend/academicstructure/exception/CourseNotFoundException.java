package com.red_escuela.backend.academicstructure.exception;

public class CourseNotFoundException extends RuntimeException {
    public CourseNotFoundException(Long id) {
        super("Course no encontrado: " + id);
    }
}
