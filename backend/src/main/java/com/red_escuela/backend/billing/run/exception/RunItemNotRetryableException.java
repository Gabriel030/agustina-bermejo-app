package com.red_escuela.backend.billing.run.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class RunItemNotRetryableException extends ConflictException {

    public RunItemNotRetryableException(Long itemId, String result) {
        super("Monthly invoice run item " + itemId + " cannot be retried while it is " + result);
    }
}
