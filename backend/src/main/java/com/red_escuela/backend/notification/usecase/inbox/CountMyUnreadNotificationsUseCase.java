package com.red_escuela.backend.notification.usecase.inbox;

import com.red_escuela.backend.notification.dto.UnreadNotificationCountResponse;

public interface CountMyUnreadNotificationsUseCase {
    UnreadNotificationCountResponse count(Long personId);
}
