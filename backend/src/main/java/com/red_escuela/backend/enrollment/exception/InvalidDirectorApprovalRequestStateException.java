package com.red_escuela.backend.enrollment.exception;

public class InvalidDirectorApprovalRequestStateException extends RuntimeException {
    public InvalidDirectorApprovalRequestStateException(Long id) {
        super("La solicitud de excepción del Director " + id + " ya fue decidida");
    }
}
