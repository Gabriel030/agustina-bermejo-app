package com.red_escuela.backend.exception;

public class CourseSubjectNotFoundException extends RuntimeException {
    public CourseSubjectNotFoundException(Long id) {
        super("CourseSubject no encontrado: " + id);
    }
}
