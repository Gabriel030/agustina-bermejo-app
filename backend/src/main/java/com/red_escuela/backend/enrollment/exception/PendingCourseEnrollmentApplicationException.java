package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PendingCourseEnrollmentApplicationException extends ConflictException {
    public PendingCourseEnrollmentApplicationException(Long studentId, Long courseId) {
        super("El student " + studentId + " ya tiene una solicitud pendiente para el curso " + courseId);
    }
}
