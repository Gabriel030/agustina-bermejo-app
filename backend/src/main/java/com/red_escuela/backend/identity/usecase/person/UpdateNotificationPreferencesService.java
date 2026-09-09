package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.dto.UpdateNotificationPreferencesRequest;
import com.red_escuela.backend.identity.entity.NotificationChannel;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.identity.entity.NotificationPreference;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.NotificationPreferenceRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@UseCase
public class UpdateNotificationPreferencesService implements UpdateNotificationPreferencesUseCase {

    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final CurrentPersonService currentPersonService;

    public UpdateNotificationPreferencesService(NotificationPreferenceRepository notificationPreferenceRepository,
                                                CurrentPersonService currentPersonService) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional
    public void updatePreferences(UpdateNotificationPreferencesRequest request) {
        Person person = currentPersonService.resolveCurrentPerson();

        Map<String, Map<String, Boolean>> incoming = request.preferences();

        for (NotificationEvent event : NotificationEvent.values()) {
            Map<String, Boolean> channels = incoming.get(event.name());
            if (channels == null) continue;

            for (NotificationChannel channel : NotificationChannel.values()) {
                Boolean enabled = channels.get(channel.name());
                if (enabled == null) continue;

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
