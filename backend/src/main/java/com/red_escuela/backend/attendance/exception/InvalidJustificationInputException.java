package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidJustificationInputException extends BadRequestException {
    public InvalidJustificationInputException(String message) {
        super(message);
    }
}
