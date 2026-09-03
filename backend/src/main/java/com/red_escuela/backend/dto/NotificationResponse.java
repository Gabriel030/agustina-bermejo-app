package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id, String type, String message, boolean read,
        String relatedEntityType, Long relatedEntityId, LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getType().name(), notification.getMessage(), notification.isRead(),
                notification.getRelatedEntityType(), notification.getRelatedEntityId(), notification.getCreatedAt());
    }
}
