package com.red_escuela.backend.graduation.exception;

public class GraduationRequirementsNotMetException extends RuntimeException {
    public GraduationRequirementsNotMetException(Long programEnrollmentId) {
        super("La matriculación " + programEnrollmentId + " no cumple los requisitos para egresar");
    }
}
