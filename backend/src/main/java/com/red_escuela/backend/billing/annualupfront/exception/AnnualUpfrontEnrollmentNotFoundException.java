package com.red_escuela.backend.billing.annualupfront.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class AnnualUpfrontEnrollmentNotFoundException extends NotFoundException {
    public AnnualUpfrontEnrollmentNotFoundException(Long programEnrollmentId) {
        super("Matriculación a carrera no encontrada: " + programEnrollmentId);
    }
}
