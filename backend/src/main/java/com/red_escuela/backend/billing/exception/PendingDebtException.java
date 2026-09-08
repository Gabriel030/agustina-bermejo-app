package com.red_escuela.backend.billing.exception;

public class PendingDebtException extends RuntimeException {
    public PendingDebtException(Long studentId) {
        super("El alumno " + studentId + " tiene facturas pendientes, no se puede verificar el egreso");
    }
}
