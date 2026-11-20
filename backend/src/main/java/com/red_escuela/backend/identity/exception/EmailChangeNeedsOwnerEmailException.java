package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class EmailChangeNeedsOwnerEmailException extends ConflictException {
    public EmailChangeNeedsOwnerEmailException(Long personId) {
        super("La persona no tiene un email vigente para aprobar el cambio: personId=" + personId);
    }
}
