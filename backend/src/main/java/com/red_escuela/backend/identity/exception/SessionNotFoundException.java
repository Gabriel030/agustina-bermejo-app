package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class SessionNotFoundException extends NotFoundException {
    public SessionNotFoundException(Long id) {
        super("Sesión no encontrada: " + id);
    }
}
