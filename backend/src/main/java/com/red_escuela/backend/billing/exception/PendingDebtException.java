package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PendingDebtException extends ConflictException {
    public PendingDebtException(Long studentId) {
        super("El alumno " + studentId + " tiene facturas pendientes, no se puede verificar el egreso");
    }
}
