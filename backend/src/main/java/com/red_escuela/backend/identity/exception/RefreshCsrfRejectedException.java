package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class RefreshCsrfRejectedException extends ForbiddenException {
    public RefreshCsrfRejectedException(String reason) {
        super("Pedido de sesión rechazado: " + reason);
    }
}
