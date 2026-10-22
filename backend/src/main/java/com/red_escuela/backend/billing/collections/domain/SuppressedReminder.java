package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;

import java.util.Objects;

public record SuppressedReminder(Long invoiceId, String noticeKey, ReminderChannel channel, String reasonCode) {

    public SuppressedReminder {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(noticeKey, "noticeKey");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(reasonCode, "reasonCode");
    }
}
