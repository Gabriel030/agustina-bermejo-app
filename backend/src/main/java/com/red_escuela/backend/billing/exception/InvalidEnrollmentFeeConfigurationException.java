package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidEnrollmentFeeConfigurationException extends BadRequestException {
    public InvalidEnrollmentFeeConfigurationException(String message) {
        super(message);
    }
}
