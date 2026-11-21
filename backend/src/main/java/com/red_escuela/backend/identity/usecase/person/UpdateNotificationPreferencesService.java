package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.dto.UpdateNotificationPreferencesRequest;
import com.red_escuela.backend.identity.service.NotificationPreferencesUpdateTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateNotificationPreferencesService implements UpdateNotificationPreferencesUseCase {

    private final NotificationPreferencesUpdateTransaction notificationPreferencesUpdateTransaction;

    public UpdateNotificationPreferencesService(NotificationPreferencesUpdateTransaction notificationPreferencesUpdateTransaction) {
        this.notificationPreferencesUpdateTransaction = notificationPreferencesUpdateTransaction;
    }

    @Override
    public void updatePreferences(UpdateNotificationPreferencesRequest request) {
        notificationPreferencesUpdateTransaction.update(request.preferences());
    }
}
