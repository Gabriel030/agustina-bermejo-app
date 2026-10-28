package com.red_escuela.backend.notification.reporting.domain;

import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;

import java.time.YearMonth;
import java.util.Objects;

public record ReminderDeliveryCount(YearMonth month, DeliveryChannel channel, DeliveryStatus status, String reason, String kind, long count) {

    public ReminderDeliveryCount {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(kind, "kind");
        if (count < 0) {
            throw new IllegalArgumentException("A count cannot be negative");
        }
    }
}
