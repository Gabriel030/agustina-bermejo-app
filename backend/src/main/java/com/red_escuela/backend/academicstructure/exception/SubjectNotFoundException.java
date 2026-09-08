package com.red_escuela.backend.academicstructure.exception;

public class SubjectNotFoundException extends RuntimeException {
    public SubjectNotFoundException(Long id) {
        super("Subject no encontrado: " + id);
    }
}
