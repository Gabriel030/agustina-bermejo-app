package com.red_escuela.backend.notification.reporting.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;

public record SentReminderEmail(Long recipientPersonId, YearMonth month, Instant sentAt) {

    public SentReminderEmail {
        Objects.requireNonNull(recipientPersonId, "recipientPersonId");
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(sentAt, "sentAt");
    }
}
