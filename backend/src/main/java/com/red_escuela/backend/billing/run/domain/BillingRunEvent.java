package com.red_escuela.backend.billing.run.domain;

public enum BillingRunEvent {
    STEP_STARTED,
    STEP_FINISHED_CLEAN,
    STEP_FINISHED_WITH_ERRORS,
    STEP_ABORTED,
    STEP_DECLARED_NOT_APPLICABLE
}
