package com.red_escuela.backend.notification.delivery.domain;

public enum DeliveryCategory {
    COLLECTION_REMINDER,
    ANNOUNCEMENT;

    public boolean isBillingFamily() {
        return this == COLLECTION_REMINDER;
    }
}
