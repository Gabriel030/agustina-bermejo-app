package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class CourseSubjectNotFoundException extends NotFoundException {
    public CourseSubjectNotFoundException(Long id) {
        super("CourseSubject no encontrado: " + id);
    }
}
