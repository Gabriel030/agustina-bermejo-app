package com.red_escuela.backend.notification.usecase.inbox;

import com.red_escuela.backend.notification.dto.UnreadNotificationCountResponse;
import com.red_escuela.backend.notification.specialist.NotificationInboxManager;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CountMyUnreadNotificationsService implements CountMyUnreadNotificationsUseCase {

    private final NotificationInboxManager notificationInboxManager;

    public CountMyUnreadNotificationsService(NotificationInboxManager notificationInboxManager) {
        this.notificationInboxManager = notificationInboxManager;
    }

    @Override
    public UnreadNotificationCountResponse count(Long personId) {
        return new UnreadNotificationCountResponse(notificationInboxManager.countUnreadForPerson(personId));
    }
}
