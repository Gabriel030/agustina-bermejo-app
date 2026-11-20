package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class WaitlistNoFreeSeatException extends ConflictException {
    public WaitlistNoFreeSeatException() {
        super("No hay lugar libre para promover a esta persona");
    }
}
