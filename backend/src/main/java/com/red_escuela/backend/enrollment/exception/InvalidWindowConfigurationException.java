package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidWindowConfigurationException extends BadRequestException {
    public InvalidWindowConfigurationException(String message) {
        super(message);
    }
}
