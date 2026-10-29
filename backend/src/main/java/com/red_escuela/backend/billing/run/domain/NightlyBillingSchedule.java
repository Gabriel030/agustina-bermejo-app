package com.red_escuela.backend.billing.run.domain;

public final class NightlyBillingSchedule {
    public static final String NIGHTLY_CRON = "0 0 3 * * *";
    public static final String WATCHDOG_CRON = "0 0 6 * * *";

    private NightlyBillingSchedule() {
    }
}
