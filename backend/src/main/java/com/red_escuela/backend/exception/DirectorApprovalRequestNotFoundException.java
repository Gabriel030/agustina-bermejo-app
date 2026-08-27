package com.red_escuela.backend.exception;

public class DirectorApprovalRequestNotFoundException extends RuntimeException {
    public DirectorApprovalRequestNotFoundException(Long id) {
        super("No existe la solicitud de excepción del Director " + id);
    }
}
