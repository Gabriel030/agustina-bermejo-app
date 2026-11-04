package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CourseAdmissionAlreadyChargedException extends ConflictException {
    public CourseAdmissionAlreadyChargedException(Long studentId, Long courseId) {
        super("El student " + studentId + " ya tiene emitido el cargo de admisión del curso " + courseId);
    }
}
