package com.red_escuela.backend.billing.run.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalBillingRunTransitionException extends ConflictException {

    public IllegalBillingRunTransitionException(String fromStatus, String event) {
        super("Event " + event + " is not allowed from billing run status " + fromStatus);
    }
}
