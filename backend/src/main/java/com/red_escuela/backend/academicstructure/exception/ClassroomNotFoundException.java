package com.red_escuela.backend.academicstructure.exception;

public class ClassroomNotFoundException extends RuntimeException {
    public ClassroomNotFoundException(Long id) {
        super("Aula no encontrada: " + id);
    }
}
