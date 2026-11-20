package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class StudentNotWithdrawnException extends ConflictException {
    public StudentNotWithdrawnException(Long studentId) {
        super("El Student " + studentId + " no está dado de baja");
    }
}
