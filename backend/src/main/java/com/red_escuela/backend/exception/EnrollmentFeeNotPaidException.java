package com.red_escuela.backend.exception;

public class EnrollmentFeeNotPaidException extends RuntimeException {
    public EnrollmentFeeNotPaidException(Long programEnrollmentId) {
        super("Program enrollment " + programEnrollmentId + " cannot enroll in subjects until its enrollment fee is paid");
    }
}
