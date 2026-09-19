package com.red_escuela.backend.academicstructure.exception;

public class CycleNotPendingApprovalException extends RuntimeException {
    public CycleNotPendingApprovalException(Long id) {
        super("El ciclo " + id + " no está en estado PENDING_APPROVAL");
    }
}
