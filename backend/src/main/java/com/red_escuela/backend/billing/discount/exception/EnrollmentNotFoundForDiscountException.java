package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class EnrollmentNotFoundForDiscountException extends NotFoundException {
    public EnrollmentNotFoundForDiscountException(String enrollmentKind, Long enrollmentId) {
        super("No existe la inscripción " + enrollmentId + " (" + enrollmentKind + ").");
    }
}
