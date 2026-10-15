package com.red_escuela.backend.shared.mail;

import com.red_escuela.backend.shared.exception.ExternalServiceException;

public class TransactionalEmailDeliveryException extends ExternalServiceException {

    public TransactionalEmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
