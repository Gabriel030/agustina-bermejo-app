package com.red_escuela.backend.academicstructure.exception;

public class IncompleteSubjectException extends RuntimeException {
    public IncompleteSubjectException(Long id, String reason) {
        super("La materia " + id + " está incompleta: " + reason);
    }
}
