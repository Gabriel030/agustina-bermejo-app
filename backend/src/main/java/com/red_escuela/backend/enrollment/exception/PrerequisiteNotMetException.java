package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class PrerequisiteNotMetException extends BadRequestException {
    public PrerequisiteNotMetException(String message) {
        super(message);
    }
}
