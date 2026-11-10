package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PendingCourseworkException extends ConflictException {
    public PendingCourseworkException(int missingApprovedCoursework) {
        super("Faltan " + missingApprovedCoursework + " trabajos prácticos aprobados para poder rendir el parcial");
    }
}
