package com.red_escuela.backend.exception;

public class PendingDebtException extends RuntimeException {
    public PendingDebtException(Long studentId) {
        super("El alumno " + studentId + " tiene facturas pendientes, no se puede verificar el egreso");
    }
}
