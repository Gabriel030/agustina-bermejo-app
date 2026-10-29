package com.red_escuela.backend.billing.run.domain;

public enum NightlyBillingRunEvent {
    NIGHT_STARTED,
    ALL_STEPS_FINISHED_CLEAN,
    ALL_STEPS_FINISHED_WITH_ERRORS,
    STEP_ABORTED
}
