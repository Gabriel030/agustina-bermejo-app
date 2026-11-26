package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidWindowConfigurationException extends BadRequestException {
    public InvalidWindowConfigurationException(String message) {
        super(message);
    }
}
