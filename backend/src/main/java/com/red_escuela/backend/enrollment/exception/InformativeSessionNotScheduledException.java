package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class InformativeSessionNotScheduledException extends UnprocessableEntityException {
    public InformativeSessionNotScheduledException(Long sessionId) {
        super("La charla informativa " + sessionId + " no está programada");
    }
}
