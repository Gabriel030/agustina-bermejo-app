package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class StudentNotActiveException extends BadRequestException {
    public StudentNotActiveException(Long studentId) {
        super("El Student " + studentId + " no está ACTIVE");
    }
}
