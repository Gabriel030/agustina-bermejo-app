package com.red_escuela.backend.notification.delivery.domain;

import java.util.List;

public enum DeliveryCategory {
    COLLECTION_REMINDER,
    ANNOUNCEMENT,
    BROADCAST,
    INVITATION;

    public static final List<DeliveryCategory> TRANSACTIONAL_EMAIL = List.of(COLLECTION_REMINDER, ANNOUNCEMENT, INVITATION);

    public boolean isBillingFamily() {
        return this == COLLECTION_REMINDER;
    }
}
