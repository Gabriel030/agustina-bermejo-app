package com.red_escuela.backend.billing.run.domain;

import java.util.Collection;

public final class NightlyBillingRunClosing {

    private NightlyBillingRunClosing() {
    }

    public static NightlyBillingRunEvent closingEventFor(Collection<BillingRunStatus> stepStatuses) {
        if (stepStatuses.contains(BillingRunStatus.FAILED)) {
            return NightlyBillingRunEvent.STEP_ABORTED;
        }
        return stepStatuses.contains(BillingRunStatus.COMPLETED_WITH_ERRORS)
                ? NightlyBillingRunEvent.ALL_STEPS_FINISHED_WITH_ERRORS
                : NightlyBillingRunEvent.ALL_STEPS_FINISHED_CLEAN;
    }
}
