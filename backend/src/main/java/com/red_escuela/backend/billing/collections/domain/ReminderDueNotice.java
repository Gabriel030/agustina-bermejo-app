package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;

import java.util.Objects;

public record ReminderDueNotice(Long invoiceId, String noticeKey, ReminderMessageType messageType, boolean summarizesPriorDebt) {

    public ReminderDueNotice {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(noticeKey, "noticeKey");
        Objects.requireNonNull(messageType, "messageType");
        if (summarizesPriorDebt && messageType != ReminderMessageType.ISSUANCE_NOTICE) {
            throw new IllegalArgumentException("Only the issuance notice can summarize the prior debt");
        }
    }

    public static ReminderDueNotice of(Long invoiceId, String noticeKey, ReminderMessageType messageType) {
        return new ReminderDueNotice(invoiceId, noticeKey, messageType, false);
    }

    public boolean isExemptFromTheDailyDigest() {
        return messageType == ReminderMessageType.BEFORE_SUSPENSION_REMINDER;
    }

    public ReminderDigestNotice asDigestNotice() {
        return new ReminderDigestNotice(noticeKey, messageType, summarizesPriorDebt);
    }
}
