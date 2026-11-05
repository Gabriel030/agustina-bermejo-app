package com.red_escuela.backend.notification.reporting.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class ReminderPaymentFollowUp {

    public static final Duration WINDOW = Duration.ofHours(72);

    private ReminderPaymentFollowUp() {
    }

    public static boolean isFollowedByPayment(Instant sentAt, List<Instant> paymentArrivals) {
        Instant deadline = sentAt.plus(WINDOW);
        return paymentArrivals.stream().anyMatch(arrival -> !arrival.isBefore(sentAt) && !arrival.isAfter(deadline));
    }
}
