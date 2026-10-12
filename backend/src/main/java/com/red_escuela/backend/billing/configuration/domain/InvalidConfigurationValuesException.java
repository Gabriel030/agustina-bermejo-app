package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidConfigurationValuesException extends BadRequestException {
    public InvalidConfigurationValuesException(String message) {
        super(message);
    }
}
