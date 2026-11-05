package com.red_escuela.backend.billing.run.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class BillingRunStepNotFoundException extends NotFoundException {

    public BillingRunStepNotFoundException(Long nightlyRunId, String step) {
        super("Billing run step " + step + " not found in nightly run " + nightlyRunId);
    }
}
