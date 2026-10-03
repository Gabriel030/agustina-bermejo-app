package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CannotWithdrawException extends ConflictException {
    public CannotWithdrawException(String message) {
        super(message);
    }
}
