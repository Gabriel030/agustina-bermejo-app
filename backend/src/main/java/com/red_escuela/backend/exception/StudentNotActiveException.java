package com.red_escuela.backend.exception;

public class StudentNotActiveException extends RuntimeException {
    public StudentNotActiveException(Long studentId) {
        super("El Student " + studentId + " no está ACTIVE");
    }
}
