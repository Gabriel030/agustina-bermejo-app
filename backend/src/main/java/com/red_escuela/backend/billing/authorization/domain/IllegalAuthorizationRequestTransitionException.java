package com.red_escuela.backend.billing.authorization.domain;

import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalAuthorizationRequestTransitionException extends ConflictException {
    public IllegalAuthorizationRequestTransitionException(AuthorizationRequestStatus from, AuthorizationRequestEvent event) {
        super("Authorization request in status " + from + " does not accept " + event);
    }
}
