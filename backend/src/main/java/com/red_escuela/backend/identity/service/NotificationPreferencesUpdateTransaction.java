package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.domain.EmittedNotificationEvents;
import com.red_escuela.backend.identity.domain.MandatoryNotificationChannels;
import com.red_escuela.backend.identity.entity.NotificationChannel;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.identity.entity.NotificationPreference;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.NotificationPreferenceRepository;
import com.red_escuela.backend.identity.specialist.CurrentPersonService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class NotificationPreferencesUpdateTransaction {
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final CurrentPersonService currentPersonService;

    public NotificationPreferencesUpdateTransaction(NotificationPreferenceRepository notificationPreferenceRepository,
                                                    CurrentPersonService currentPersonService) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public void update(Map<String, Map<String, Boolean>> incoming) {
        Person person = currentPersonService.resolveCurrentPerson();

        for (NotificationEvent event : EmittedNotificationEvents.visible()) {
            Map<String, Boolean> channels = incoming.get(event.name());
            if (channels == null) continue;

            for (NotificationChannel channel : NotificationChannel.values()) {
                Boolean enabled = channels.get(channel.name());
                if (enabled == null) continue;
                if (!enabled && MandatoryNotificationChannels.cannotBeTurnedOff(event, channel)) continue;

                Optional<NotificationPreference> existing =
                        notificationPreferenceRepository.findByPersonAndEventAndChannel(person, event, channel);

                if (enabled) {
                    existing.ifPresent(notificationPreferenceRepository::delete);
                } else {
                    NotificationPreference pref = existing.orElseGet(() -> {
                        NotificationPreference p = new NotificationPreference();
                        p.setPerson(person);
                        p.setEvent(event);
                        p.setChannel(channel);
                        return p;
                    });
                    pref.setEnabled(false);
                    notificationPreferenceRepository.save(pref);
                }
            }
        }
    }
}
