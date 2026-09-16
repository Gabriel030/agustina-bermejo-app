package com.red_escuela.backend.graduation.exception;

public class GraduationRequestAlreadyActiveException extends RuntimeException {
    public GraduationRequestAlreadyActiveException(Long programEnrollmentId) {
        super("Ya existe un trámite de egreso activo para la matriculación " + programEnrollmentId);
    }
}
