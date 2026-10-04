package com.red_escuela.backend.enrollment.exception;

public class EnrollmentFeeNotPaidForTermException extends EnrollmentFeeNotPaidException {
    public EnrollmentFeeNotPaidForTermException(Long programEnrollmentId, Long templateId, int slot) {
        super(programEnrollmentId, "Program enrollment " + programEnrollmentId + " has not paid the enrollment fee " + slot
                + " of year cycle " + templateId);
    }
}
