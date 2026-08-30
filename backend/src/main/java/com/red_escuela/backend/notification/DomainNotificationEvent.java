package com.red_escuela.backend.notification;

import com.red_escuela.backend.entity.NotificationType;

import java.util.List;

public interface DomainNotificationEvent {
    NotificationType type();

    String message();

    List<NotificationRecipient> recipients();

    String relatedEntityType();

    Long relatedEntityId();
}
