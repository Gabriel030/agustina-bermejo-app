package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class AlreadyEnrolledInProgramException extends ConflictException {
    public AlreadyEnrolledInProgramException(Long studentId, Long programId) {
        super("El Student " + studentId + " ya tiene una matriculación activa a la carrera " + programId);
    }
}
