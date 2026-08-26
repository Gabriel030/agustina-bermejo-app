package com.red_escuela.backend.exception;

public class StudentNotConfirmedException extends RuntimeException {
    public StudentNotConfirmedException(Long studentId) {
        super("El Student " + studentId + " todavía no está CONFIRMADO");
    }
}
