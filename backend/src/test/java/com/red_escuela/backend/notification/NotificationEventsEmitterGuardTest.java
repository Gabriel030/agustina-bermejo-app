package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.domain.EmittedNotificationEvents;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventsEmitterGuardTest {

    private final Set<NotificationEvent> resolvedEvents = Arrays.stream(NotificationType.values())
            .map(UserPreferenceEventResolver::resolve)
            .flatMap(java.util.Optional::stream)
            .collect(Collectors.toSet());

    @Test
    void everyVisibleEventIsResolvedByANotificationType() {
        assertThat(resolvedEvents).containsAll(EmittedNotificationEvents.visible());
    }

    @Test
    void noHiddenEventIsResolvedByANotificationType() {
        assertThat(resolvedEvents).doesNotContainAnyElementsOf(EmittedNotificationEvents.unemitted());
    }
}
