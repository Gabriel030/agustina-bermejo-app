package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.dto.UpdateNotificationPreferencesRequest;

public interface UpdateNotificationPreferencesUseCase {
    void updatePreferences(UpdateNotificationPreferencesRequest request);
}
