package com.red_escuela.backend.academicstructure.domain;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class CourseNotFoundException extends NotFoundException {
    public CourseNotFoundException(Long id) {
        super("Course no encontrado: " + id);
    }
}
