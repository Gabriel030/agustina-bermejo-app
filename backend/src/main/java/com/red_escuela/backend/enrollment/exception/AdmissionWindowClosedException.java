package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class AdmissionWindowClosedException extends BadRequestException {
    public AdmissionWindowClosedException(String message) {
        super(message);
    }
}
