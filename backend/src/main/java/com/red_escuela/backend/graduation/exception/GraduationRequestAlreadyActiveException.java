package com.red_escuela.backend.graduation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class GraduationRequestAlreadyActiveException extends ConflictException {
    public GraduationRequestAlreadyActiveException(Long programEnrollmentId) {
        super("Ya existe un trámite de egreso activo para la matriculación " + programEnrollmentId);
    }
}
