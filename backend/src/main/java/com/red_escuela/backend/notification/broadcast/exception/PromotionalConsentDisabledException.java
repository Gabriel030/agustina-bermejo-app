package com.red_escuela.backend.notification.broadcast.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class PromotionalConsentDisabledException extends ConflictException {

    public PromotionalConsentDisabledException() {
        super("Promotional consent is disabled");
    }
}
