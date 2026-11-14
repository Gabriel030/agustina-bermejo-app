package com.red_escuela.backend.notification.usecase.inbox;

import com.red_escuela.backend.notification.dto.NotificationResponse;

public interface MarkMyNotificationAsReadUseCase {
    NotificationResponse markAsRead(Long notificationId, Long personId);
}
