package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class EnrollmentFeeExemptionNotFoundException extends NotFoundException {
    public EnrollmentFeeExemptionNotFoundException(Long id) {
        super("Enrollment fee exemption not found: " + id);
    }
}
