package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class WaitlistEntryNotWaitingException extends ConflictException {
    public WaitlistEntryNotWaitingException() {
        super("La persona ya no está en la lista de espera");
    }
}
