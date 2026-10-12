package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidPasswordPolicyException extends BadRequestException {
    public InvalidPasswordPolicyException(String message) {
        super(message);
    }
}
