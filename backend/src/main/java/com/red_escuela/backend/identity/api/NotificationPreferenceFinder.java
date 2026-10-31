package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.NotificationChannel;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.identity.entity.NotificationPreference;
import com.red_escuela.backend.identity.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationPreferenceFinder {

    private final NotificationPreferenceRepository notificationPreferenceRepository;

    public NotificationPreferenceFinder(NotificationPreferenceRepository notificationPreferenceRepository) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
    }

    @Transactional(readOnly = true)
    public boolean isPreDueReminderEmailEnabled(Long personId) {
        return notificationPreferenceRepository
                .findByPersonIdAndEventAndChannel(personId, NotificationEvent.PAYMENT_DUE_SOON, NotificationChannel.EMAIL)
                .map(NotificationPreference::isEnabled)
                .orElse(true);
    }
}
