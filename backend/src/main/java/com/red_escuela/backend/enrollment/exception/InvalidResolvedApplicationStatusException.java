package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidResolvedApplicationStatusException extends BadRequestException {
    public InvalidResolvedApplicationStatusException() {
        super("El historial solo admite solicitudes resueltas: APPROVED, REJECTED o EXPIRED");
    }
}
