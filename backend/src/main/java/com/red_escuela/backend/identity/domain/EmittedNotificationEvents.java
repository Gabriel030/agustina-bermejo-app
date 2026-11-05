package com.red_escuela.backend.identity.domain;

import com.red_escuela.backend.identity.entity.NotificationEvent;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class EmittedNotificationEvents {

    private static final Set<NotificationEvent> UNEMITTED = EnumSet.of(
            NotificationEvent.ACCOUNT_CREATED,
            NotificationEvent.GRADE_POSTED,
            NotificationEvent.INVOICE_GENERATED);

    private EmittedNotificationEvents() {
    }

    public static List<NotificationEvent> visible() {
        return Arrays.stream(NotificationEvent.values())
                .filter(event -> !UNEMITTED.contains(event))
                .toList();
    }

    public static Set<NotificationEvent> unemitted() {
        return EnumSet.copyOf(UNEMITTED);
    }
}
