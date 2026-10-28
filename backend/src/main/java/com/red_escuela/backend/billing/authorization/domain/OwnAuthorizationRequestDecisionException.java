package com.red_escuela.backend.billing.authorization.domain;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class OwnAuthorizationRequestDecisionException extends ForbiddenException {
    public OwnAuthorizationRequestDecisionException(Long id) {
        super("Nobody decides an authorization request they requested themselves: " + id);
    }
}
