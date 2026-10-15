package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class EnrollmentFeeExemptionNotNeededException extends ConflictException {
    public EnrollmentFeeExemptionNotNeededException(String message) {
        super(message);
    }
}
