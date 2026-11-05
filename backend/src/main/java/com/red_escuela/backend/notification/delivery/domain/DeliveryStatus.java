package com.red_escuela.backend.notification.delivery.domain;

public enum DeliveryStatus {
    QUEUED,
    SENT,
    SENT_SIMULATED,
    SUPPRESSED,
    DEFERRED,
    DISCARDED,
    FAILED;

    public boolean requiresReason() {
        return this == SUPPRESSED || this == DEFERRED || this == DISCARDED || this == FAILED;
    }

    public boolean isWaitingForDispatch() {
        return this == QUEUED || this == DEFERRED;
    }
}
