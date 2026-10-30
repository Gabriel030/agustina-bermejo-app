package com.red_escuela.backend.billing.run.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ManualIssuanceRequiresSimulationException extends ConflictException {

    public ManualIssuanceRequiresSimulationException() {
        super("Manual issuance requires a complete simulation of the same period made today");
    }
}
