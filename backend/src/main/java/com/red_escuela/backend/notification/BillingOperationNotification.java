package com.red_escuela.backend.notification;

import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record BillingOperationNotification(NotificationType type, String message, List<NotificationRecipient> recipients,
                                           String relatedEntityType, Long relatedEntityId) implements DomainNotificationEvent {
}
