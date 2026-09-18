package com.red_escuela.backend.enrollment.exception;

public class InvalidSectionOverridesException extends RuntimeException {
    public InvalidSectionOverridesException(String sectionOverrides, Throwable cause) {
        super("sectionOverrides inválido: " + sectionOverrides, cause);
    }
}
