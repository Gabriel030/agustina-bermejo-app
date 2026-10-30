package com.red_escuela.backend.billing.run.domain;

import java.time.LocalTime;

public final class FailedInvoiceRetryWindow {
    public static final LocalTime OPENS_AT = LocalTime.of(6, 0);
    public static final LocalTime CLOSES_AT = LocalTime.of(22, 0);

    private FailedInvoiceRetryWindow() {
    }

    public static boolean isOpenAt(LocalTime time) {
        return !time.isBefore(OPENS_AT) && time.isBefore(CLOSES_AT);
    }
}
