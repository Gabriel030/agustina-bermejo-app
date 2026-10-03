package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvalidDirectorApprovalRequestStateException extends ConflictException {
    public InvalidDirectorApprovalRequestStateException(Long id) {
        super("La solicitud de excepción del Director " + id + " ya fue decidida");
    }
}
