package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CannotJustifyException extends ConflictException {
    public CannotJustifyException(String message) {
        super(message);
    }
}
