package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;

import java.util.Objects;

public record ReminderDigestNotice(String noticeKey, ReminderMessageType messageType) {

    public ReminderDigestNotice {
        Objects.requireNonNull(noticeKey, "noticeKey");
        Objects.requireNonNull(messageType, "messageType");
    }
}
