package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class EnrollmentFeeNotPaidException extends BadRequestException {
    protected EnrollmentFeeNotPaidException(Long programEnrollmentId, String message) {
        super(message);
    }

    public EnrollmentFeeNotPaidException(Long programEnrollmentId) {
        super("Program enrollment " + programEnrollmentId + " cannot enroll in subjects until its enrollment fee is paid");
    }
}
