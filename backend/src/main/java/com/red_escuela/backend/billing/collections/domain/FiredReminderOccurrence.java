package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;

import java.time.LocalDate;
import java.util.Objects;

public record FiredReminderOccurrence(Long invoiceId, String noticeKey, ReminderChannel channel, LocalDate date) {

    public FiredReminderOccurrence {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(noticeKey, "noticeKey");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(date, "date");
    }
}
