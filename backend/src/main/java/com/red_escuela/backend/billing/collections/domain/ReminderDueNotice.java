package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;

import java.util.Objects;

public record ReminderDueNotice(Long invoiceId, String noticeKey, ReminderMessageType messageType) {

    public ReminderDueNotice {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(noticeKey, "noticeKey");
        Objects.requireNonNull(messageType, "messageType");
    }

    public boolean isExemptFromTheDailyDigest() {
        return messageType == ReminderMessageType.BEFORE_SUSPENSION_REMINDER;
    }

    public ReminderDigestNotice asDigestNotice() {
        return new ReminderDigestNotice(noticeKey, messageType);
    }
}
