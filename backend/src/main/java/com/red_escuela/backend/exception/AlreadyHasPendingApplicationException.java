package com.red_escuela.backend.exception;

public class AlreadyHasPendingApplicationException extends RuntimeException {
    public AlreadyHasPendingApplicationException(Long studentId, Long programId) {
        super("El student " + studentId + " ya tiene una solicitud pendiente para el program " + programId);
    }
}
