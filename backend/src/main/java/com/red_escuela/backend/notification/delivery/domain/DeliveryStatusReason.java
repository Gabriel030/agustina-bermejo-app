package com.red_escuela.backend.notification.delivery.domain;

public final class DeliveryStatusReason {

    public static final int MAX_LENGTH = 60;
    public static final String OPTED_OUT = "OPTED_OUT";
    public static final String NO_VERIFIED_EMAIL = "NO_VERIFIED_EMAIL";
    public static final String PROVIDER_ERROR = "PROVIDER_ERROR";
    public static final String NO_LONGER_TIMELY = "NO_LONGER_TIMELY";
    public static final String NO_LONGER_APPLICABLE = "NO_LONGER_APPLICABLE";
    public static final String DAILY_CAP_REACHED = "DAILY_CAP_REACHED";

    private DeliveryStatusReason() {
    }

    public static String require(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A status reason cannot be blank");
        }
        if (reason.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("A status reason is at most " + MAX_LENGTH + " characters");
        }
        return reason;
    }
}
