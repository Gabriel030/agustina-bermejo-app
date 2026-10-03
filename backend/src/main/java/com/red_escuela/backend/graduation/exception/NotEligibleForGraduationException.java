package com.red_escuela.backend.graduation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class NotEligibleForGraduationException extends BadRequestException {
    public NotEligibleForGraduationException(Long programEnrollmentId) {
        super("La matriculación " + programEnrollmentId + " no es elegible para completar la carrera todavía");
    }
}
