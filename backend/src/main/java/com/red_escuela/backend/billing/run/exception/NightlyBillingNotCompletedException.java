package com.red_escuela.backend.billing.run.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class NightlyBillingNotCompletedException extends ConflictException {

    public NightlyBillingNotCompletedException() {
        super("Manual issuance requires the nightly billing run of today to be completed");
    }
}
