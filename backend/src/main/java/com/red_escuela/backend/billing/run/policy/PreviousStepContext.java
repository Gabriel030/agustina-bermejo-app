package com.red_escuela.backend.billing.run.policy;

import com.red_escuela.backend.billing.run.domain.BillingRunStatus;

public record PreviousStepContext(BillingRunStatus previousStepStatus) {

    public static PreviousStepContext beforeTheFirstStep() {
        return new PreviousStepContext(null);
    }
}
