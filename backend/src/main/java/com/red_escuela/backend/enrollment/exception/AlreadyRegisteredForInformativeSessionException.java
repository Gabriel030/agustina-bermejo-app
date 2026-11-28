package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class AlreadyRegisteredForInformativeSessionException extends ConflictException {
    public AlreadyRegisteredForInformativeSessionException(Long sessionId, Long personId) {
        super("La persona " + personId + " ya reservó lugar en la charla informativa " + sessionId);
    }
}
