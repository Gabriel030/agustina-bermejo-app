package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CoursePaymentPlanAlreadySetException extends ConflictException {
    public CoursePaymentPlanAlreadySetException(Long courseEnrollmentId) {
        super("Course enrollment " + courseEnrollmentId + " already has a payment plan — it is immutable for courses");
    }
}
