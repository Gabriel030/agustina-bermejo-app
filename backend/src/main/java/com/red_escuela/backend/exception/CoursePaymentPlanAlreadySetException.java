package com.red_escuela.backend.exception;

public class CoursePaymentPlanAlreadySetException extends RuntimeException {
    public CoursePaymentPlanAlreadySetException(Long courseEnrollmentId) {
        super("Course enrollment " + courseEnrollmentId + " already has a payment plan — it is immutable for courses");
    }
}
