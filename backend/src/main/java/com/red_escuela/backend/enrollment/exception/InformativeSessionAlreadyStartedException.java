package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class InformativeSessionAlreadyStartedException extends UnprocessableEntityException {
    public InformativeSessionAlreadyStartedException(Long sessionId) {
        super("La charla informativa " + sessionId + " ya comenzó");
    }
}
