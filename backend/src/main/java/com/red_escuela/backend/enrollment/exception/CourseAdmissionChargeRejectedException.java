package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CourseAdmissionChargeRejectedException extends ConflictException {
    public CourseAdmissionChargeRejectedException(String denials) {
        super("El cargo de admisión al curso fue rechazado: " + denials);
    }
}
