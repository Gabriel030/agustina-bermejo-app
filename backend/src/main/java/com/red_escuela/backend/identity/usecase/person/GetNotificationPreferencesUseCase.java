package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.dto.NotificationPreferencesResponse;

public interface GetNotificationPreferencesUseCase {
    NotificationPreferencesResponse getPreferences();
}
