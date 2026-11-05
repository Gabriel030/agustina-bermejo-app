package com.red_escuela.backend.identity.entity;

import org.springframework.modulith.NamedInterface;

import java.util.Set;

@NamedInterface("model")
public enum NotificationEvent {
    ACCOUNT_CREATED(NotificationChannel.APP, NotificationChannel.EMAIL),
    SUBJECT_ENROLLMENT,
    GRADE_POSTED,
    INVOICE_GENERATED,
    PAYMENT_DUE_SOON(NotificationChannel.APP),
    EXAM_BOARD_REGISTERED,
    INSTITUTIONAL_NOTICE,
    HOLIDAY_CALENDAR_REMINDER;

    private final Set<NotificationChannel> mandatoryChannels;

    NotificationEvent(NotificationChannel... mandatoryChannels) {
        this.mandatoryChannels = mandatoryChannels.length == 0 ? Set.of() : Set.of(mandatoryChannels);
    }

    public boolean isMandatoryOn(NotificationChannel channel) {
        return mandatoryChannels.contains(channel);
    }
}
