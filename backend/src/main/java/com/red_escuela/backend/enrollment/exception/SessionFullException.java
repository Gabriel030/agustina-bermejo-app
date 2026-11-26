package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SessionFullException extends ConflictException {
    public SessionFullException(Long sessionId) {
        super("La charla informativa " + sessionId + " no tiene lugares disponibles");
    }
}
