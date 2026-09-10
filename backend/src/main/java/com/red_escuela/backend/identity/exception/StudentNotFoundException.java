package com.red_escuela.backend.identity.exception;

public class StudentNotFoundException extends RuntimeException {
    public StudentNotFoundException(Long id) {
        super("Student no encontrado: " + id);
    }
}
