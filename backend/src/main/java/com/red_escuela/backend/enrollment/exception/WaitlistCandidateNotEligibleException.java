package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class WaitlistCandidateNotEligibleException extends ConflictException {
    public WaitlistCandidateNotEligibleException() {
        super("La persona no cumple los requisitos para ser promovida");
    }
}
