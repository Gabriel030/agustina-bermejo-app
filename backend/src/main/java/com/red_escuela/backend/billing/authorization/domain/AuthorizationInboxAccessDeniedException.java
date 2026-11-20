package com.red_escuela.backend.billing.authorization.domain;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class AuthorizationInboxAccessDeniedException extends ForbiddenException {
    public AuthorizationInboxAccessDeniedException() {
        super("Not allowed to read the authorization requests");
    }
}
