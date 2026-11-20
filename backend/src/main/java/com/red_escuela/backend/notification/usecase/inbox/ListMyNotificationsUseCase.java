package com.red_escuela.backend.notification.usecase.inbox;

import com.red_escuela.backend.notification.dto.NotificationResponse;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListMyNotificationsUseCase {
    PageResponse<NotificationResponse> list(Long personId, int page, int size);
}
