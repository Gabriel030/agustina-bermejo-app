package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidPrerequisiteException extends BadRequestException {
    public InvalidPrerequisiteException(String message) {
        super(message);
    }
}
