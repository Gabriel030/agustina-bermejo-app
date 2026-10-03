package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ProspectAlreadyConfirmedException extends ConflictException {
    public ProspectAlreadyConfirmedException(Long studentId) {
        super("El prospecto ya fue confirmado: " + studentId);
    }
}
