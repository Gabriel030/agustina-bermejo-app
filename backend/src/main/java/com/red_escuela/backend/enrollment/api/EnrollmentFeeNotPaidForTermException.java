package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.exception.EnrollmentFeeNotPaidException;

public class EnrollmentFeeNotPaidForTermException extends EnrollmentFeeNotPaidException {
    public EnrollmentFeeNotPaidForTermException(Long programEnrollmentId, Long templateId, int slot) {
        super(programEnrollmentId, "Program enrollment " + programEnrollmentId + " has not paid the enrollment fee " + slot
                + " of year cycle " + templateId);
    }
}
