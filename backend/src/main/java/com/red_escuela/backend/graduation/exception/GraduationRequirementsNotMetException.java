package com.red_escuela.backend.graduation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GraduationRequirementsNotMetException extends ConflictException {
    public GraduationRequirementsNotMetException(Long programEnrollmentId) {
        super("La matriculación " + programEnrollmentId + " no cumple los requisitos para egresar");
    }
}
