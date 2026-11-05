package com.red_escuela.backend.platform.domain;

public record BillingModeChangeClearance(boolean cutoverCompleted, boolean preflightPassed) {

    public static final BillingModeChangeClearance NONE = new BillingModeChangeClearance(false, false);
}
