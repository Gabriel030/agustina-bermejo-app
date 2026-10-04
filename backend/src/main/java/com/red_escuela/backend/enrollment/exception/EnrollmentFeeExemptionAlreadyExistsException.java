package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class EnrollmentFeeExemptionAlreadyExistsException extends ConflictException {
    public EnrollmentFeeExemptionAlreadyExistsException(Long programEnrollmentId, Long templateId, int slot) {
        super("Program enrollment " + programEnrollmentId + " already has an active exemption for cycle " + templateId + " slot " + slot);
    }
}
