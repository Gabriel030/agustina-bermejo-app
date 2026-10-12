package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidCorequisiteException extends BadRequestException {
    public InvalidCorequisiteException(String message) {
        super(message);
    }
}
