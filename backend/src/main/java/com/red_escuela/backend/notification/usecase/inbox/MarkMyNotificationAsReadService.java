package com.red_escuela.backend.notification.usecase.inbox;

import com.red_escuela.backend.notification.dto.NotificationResponse;
import com.red_escuela.backend.notification.specialist.NotificationInboxManager;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class MarkMyNotificationAsReadService implements MarkMyNotificationAsReadUseCase {

    private final NotificationInboxManager notificationInboxManager;

    public MarkMyNotificationAsReadService(NotificationInboxManager notificationInboxManager) {
        this.notificationInboxManager = notificationInboxManager;
    }

    @Override
    public NotificationResponse markAsRead(Long notificationId, Long personId) {
        return notificationInboxManager.markAsRead(notificationId, personId);
    }
}
