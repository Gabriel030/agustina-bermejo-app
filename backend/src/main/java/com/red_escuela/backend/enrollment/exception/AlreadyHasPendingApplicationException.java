package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class AlreadyHasPendingApplicationException extends ConflictException {
    public AlreadyHasPendingApplicationException(Long studentId, Long programId) {
        super("El student " + studentId + " ya tiene una solicitud pendiente para el program " + programId);
    }
}
