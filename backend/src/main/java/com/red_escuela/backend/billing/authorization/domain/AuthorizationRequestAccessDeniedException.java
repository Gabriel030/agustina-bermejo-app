package com.red_escuela.backend.billing.authorization.domain;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class AuthorizationRequestAccessDeniedException extends ForbiddenException {
    public AuthorizationRequestAccessDeniedException(Long id) {
        super("Not allowed to decide authorization request " + id);
    }
}
