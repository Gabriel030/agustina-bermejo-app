package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class EnrollmentFeeExemptionAlreadyRevokedException extends ConflictException {
    public EnrollmentFeeExemptionAlreadyRevokedException(Long id) {
        super("Enrollment fee exemption already revoked: " + id);
    }
}
