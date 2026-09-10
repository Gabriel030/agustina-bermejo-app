package com.red_escuela.backend.enrollment.exception;

public class AlreadyEnrolledInCourseException extends RuntimeException {
    public AlreadyEnrolledInCourseException(Long studentId, Long courseId) {
        super("El Student " + studentId + " ya tiene una matriculación activa al curso " + courseId);
    }
}
