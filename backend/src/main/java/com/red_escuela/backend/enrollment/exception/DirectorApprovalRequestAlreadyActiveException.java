package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class DirectorApprovalRequestAlreadyActiveException extends ConflictException {
    public DirectorApprovalRequestAlreadyActiveException(Long programEnrollmentId) {
        super("Ya existe una excepción de este tipo pendiente para la matriculación " + programEnrollmentId);
    }
}
