package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidSectionOverridesException extends BadRequestException {
    public InvalidSectionOverridesException(String sectionOverrides, Throwable cause) {
        super("sectionOverrides inválido: " + sectionOverrides, cause);
    }
}
