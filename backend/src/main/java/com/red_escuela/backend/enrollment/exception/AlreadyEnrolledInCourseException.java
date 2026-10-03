package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class AlreadyEnrolledInCourseException extends ConflictException {
    public AlreadyEnrolledInCourseException(Long studentId, Long courseId) {
        super("El Student " + studentId + " ya tiene una matriculación activa al curso " + courseId);
    }
}
