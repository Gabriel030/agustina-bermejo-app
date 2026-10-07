package com.red_escuela.backend.billing.authorization.domain;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class AuthorizationRequestNotFoundException extends NotFoundException {
    public AuthorizationRequestNotFoundException(Long id) {
        super("Authorization request not found: " + id);
    }
}
