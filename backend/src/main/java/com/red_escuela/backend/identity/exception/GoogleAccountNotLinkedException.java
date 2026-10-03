package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class GoogleAccountNotLinkedException extends NotFoundException {
    public GoogleAccountNotLinkedException(String email) {
        super("No existe una cuenta para el email " + email);
    }
}
