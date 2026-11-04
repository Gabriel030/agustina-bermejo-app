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
        return isEnabled(personId, NotificationEvent.PAYMENT_DUE_SOON, NotificationChannel.EMAIL);
    }

    public boolean isInAppEnabled(Long personId, NotificationEvent event) {
        return isEnabled(personId, event, NotificationChannel.APP);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(Long personId, NotificationEvent event, NotificationChannel channel) {
        if (event.isMandatoryOn(channel)) {
            return true;
        }
        return notificationPreferenceRepository
                .findByPersonIdAndEventAndChannel(personId, event, channel)
                .map(NotificationPreference::isEnabled)
                .orElse(true);
    }
}
