package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.exception.ConflictException;

public class AuthorizationPendingException extends ConflictException {
    private final Long authorizationRequestId;

    public AuthorizationPendingException(long authorizationRequestId) {
        super("The operation needs an authorization before it is applied: " + authorizationRequestId);
        this.authorizationRequestId = authorizationRequestId;
    }

    public Long authorizationRequestId() {
        return authorizationRequestId;
    }
}
