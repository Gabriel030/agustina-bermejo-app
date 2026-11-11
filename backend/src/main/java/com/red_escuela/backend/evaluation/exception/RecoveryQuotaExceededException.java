package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class RecoveryQuotaExceededException extends ConflictException {
    public RecoveryQuotaExceededException(int recoveryQuota) {
        super("Se alcanzó el tope de " + recoveryQuota + " recuperatorios de la materia");
    }
}
