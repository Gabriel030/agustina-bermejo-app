package com.red_escuela.backend.billing.run.domain;

import java.time.LocalDate;
import java.util.function.Predicate;

public final class DraftReviewAlertSchedule {
    public static final int FIRST_ALERT_DAY_OF_PERIOD = 5;

    private DraftReviewAlertSchedule() {
    }

    public static boolean alertsOn(LocalDate period, LocalDate businessDate, Predicate<LocalDate> isBusinessDay) {
        LocalDate firstAlertDay = period.withDayOfMonth(FIRST_ALERT_DAY_OF_PERIOD);
        return businessDate.equals(firstAlertDay) || (businessDate.isAfter(firstAlertDay) && isBusinessDay.test(businessDate));
    }
}
