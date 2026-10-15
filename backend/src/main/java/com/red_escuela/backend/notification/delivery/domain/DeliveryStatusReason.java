package com.red_escuela.backend.notification.delivery.domain;

public final class DeliveryStatusReason {

    public static final int MAX_LENGTH = 60;
    public static final String OPTED_OUT = "OPTED_OUT";
    public static final String NO_VERIFIED_EMAIL = "NO_VERIFIED_EMAIL";
    public static final String PROVIDER_ERROR = "PROVIDER_ERROR";

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
