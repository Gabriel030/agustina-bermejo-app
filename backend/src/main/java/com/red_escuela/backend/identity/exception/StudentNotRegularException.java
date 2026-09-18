package com.red_escuela.backend.identity.exception;

public class StudentNotRegularException extends RuntimeException {
    public StudentNotRegularException(Long enrollmentId) {
        super("La cursada " + enrollmentId + " no está REGULAR, no puede inscribirse a una mesa de examen");
    }
}
