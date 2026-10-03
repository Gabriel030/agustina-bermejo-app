package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidSectionChangeException extends BadRequestException {
    public InvalidSectionChangeException(String message) {
        super(message);
    }
}
