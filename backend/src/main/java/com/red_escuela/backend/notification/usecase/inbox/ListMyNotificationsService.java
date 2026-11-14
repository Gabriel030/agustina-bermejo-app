package com.red_escuela.backend.notification.usecase.inbox;

import com.red_escuela.backend.notification.dto.NotificationResponse;
import com.red_escuela.backend.notification.specialist.NotificationInboxManager;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class ListMyNotificationsService implements ListMyNotificationsUseCase {

    private final NotificationInboxManager notificationInboxManager;

    public ListMyNotificationsService(NotificationInboxManager notificationInboxManager) {
        this.notificationInboxManager = notificationInboxManager;
    }

    @Override
    public PageResponse<NotificationResponse> list(Long personId, int page, int size) {
        return notificationInboxManager.pageForPerson(personId, page, size);
    }
}
