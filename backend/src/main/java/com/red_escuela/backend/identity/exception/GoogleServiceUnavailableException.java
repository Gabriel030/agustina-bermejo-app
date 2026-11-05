package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ExternalServiceException;

public class GoogleServiceUnavailableException extends ExternalServiceException {
    public GoogleServiceUnavailableException(Throwable cause) {
        super("Google no respondió", cause);
    }
}
