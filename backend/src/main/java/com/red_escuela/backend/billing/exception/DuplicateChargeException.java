package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class DuplicateChargeException extends ConflictException {
    public DuplicateChargeException(String detail) {
        super("Duplicate charge detected: " + detail);
    }
}
