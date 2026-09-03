package com.red_escuela.backend.exception;

public class DirectorApprovalRequestAlreadyActiveException extends RuntimeException {
    public DirectorApprovalRequestAlreadyActiveException(Long programEnrollmentId) {
        super("Ya existe una excepción de este tipo pendiente para la matriculación " + programEnrollmentId);
    }
}
