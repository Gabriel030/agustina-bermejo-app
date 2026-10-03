package com.red_escuela.backend.enrollment.exception;

public class PendingCourseEnrollmentApplicationException extends RuntimeException {
    public PendingCourseEnrollmentApplicationException(Long studentId, Long courseId) {
        super("El student " + studentId + " ya tiene una solicitud pendiente para el curso " + courseId);
    }
}
