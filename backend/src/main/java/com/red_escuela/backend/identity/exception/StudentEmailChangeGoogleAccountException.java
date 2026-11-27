package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class StudentEmailChangeGoogleAccountException extends UnprocessableEntityException {
    public StudentEmailChangeGoogleAccountException(Long studentId) {
        super("El email del Student " + studentId + " no se edita por esta vía: la cuenta inicia sesión con Google");
    }
}
