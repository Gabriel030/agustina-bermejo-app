package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class EmailChangePendingByOwnerException extends ConflictException {
    public EmailChangePendingByOwnerException(Long personId) {
        super("La persona ya inició un cambio de email que sigue pendiente: personId=" + personId);
    }
}
