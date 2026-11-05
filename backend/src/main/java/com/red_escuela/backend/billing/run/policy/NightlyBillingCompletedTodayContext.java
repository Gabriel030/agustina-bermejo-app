package com.red_escuela.backend.billing.run.policy;

import com.red_escuela.backend.billing.run.domain.NightlyBillingRunStatus;

import java.time.LocalDate;

public record NightlyBillingCompletedTodayContext(LocalDate businessDate, LocalDate latestRunDate, NightlyBillingRunStatus latestRunStatus) {

    public static NightlyBillingCompletedTodayContext withoutAnyRun(LocalDate businessDate) {
        return new NightlyBillingCompletedTodayContext(businessDate, null, null);
    }
}
