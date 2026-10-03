package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class EnrollmentWindowClosedException extends BadRequestException {
    public EnrollmentWindowClosedException(String message) {
        super(message);
    }
}
