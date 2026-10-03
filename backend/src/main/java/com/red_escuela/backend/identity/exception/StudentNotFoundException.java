package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class StudentNotFoundException extends NotFoundException {
    public StudentNotFoundException(Long id) {
        super("Student no encontrado: " + id);
    }
}
