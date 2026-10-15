package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CycleNotPendingApprovalException extends ConflictException {
    public CycleNotPendingApprovalException(Long id) {
        super("El ciclo " + id + " no está en estado PENDING_APPROVAL");
    }
}
