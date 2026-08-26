package com.red_escuela.backend.exception;

public class StudentNotFoundException extends RuntimeException {
    public StudentNotFoundException(Long id) {
        super("Student no encontrado: " + id);
    }
}
