package com.red_escuela.backend.enrollment.exception;

public class ProspectAlreadyConfirmedException extends RuntimeException {
    public ProspectAlreadyConfirmedException(Long studentId) {
        super("El prospecto ya fue confirmado: " + studentId);
    }
}
