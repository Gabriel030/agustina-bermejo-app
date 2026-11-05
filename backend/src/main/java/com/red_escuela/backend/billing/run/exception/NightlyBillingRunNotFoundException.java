package com.red_escuela.backend.billing.run.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class NightlyBillingRunNotFoundException extends NotFoundException {

    public NightlyBillingRunNotFoundException(Long id) {
        super("Nightly billing run not found: " + id);
    }
}
