package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class StudentNotConfirmedException extends BadRequestException {
    public StudentNotConfirmedException(Long studentId) {
        super("El Student " + studentId + " todavía no está CONFIRMADO");
    }
}
