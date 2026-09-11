package com.red_escuela.backend.enrollment.exception;

public class AlreadyEnrolledInProgramException extends RuntimeException {
    public AlreadyEnrolledInProgramException(Long studentId, Long programId) {
        super("El Student " + studentId + " ya tiene una matriculación activa a la carrera " + programId);
    }
}
