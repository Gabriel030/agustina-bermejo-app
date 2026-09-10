package com.red_escuela.backend.identity.exception;

public class GoogleAccountNotLinkedException extends RuntimeException {
    public GoogleAccountNotLinkedException(String email) {
        super("No existe una cuenta para el email " + email);
    }
}
