package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.dto.NotificationPreferencesResponse;
import com.red_escuela.backend.identity.entity.NotificationChannel;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.identity.entity.NotificationPreference;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.NotificationPreferenceRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.identity.domain.MandatoryNotificationChannels;
import com.red_escuela.backend.shared.UseCase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UseCase
public class GetNotificationPreferencesService implements GetNotificationPreferencesUseCase {

    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final CurrentPersonService currentPersonService;

    public GetNotificationPreferencesService(NotificationPreferenceRepository notificationPreferenceRepository,
                                             CurrentPersonService currentPersonService) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.currentPersonService = currentPersonService;
    }

    @Override
    public NotificationPreferencesResponse getPreferences() {
        Person person = currentPersonService.resolveCurrentPerson();
        List<NotificationPreference> stored = notificationPreferenceRepository.findByPerson(person);

        Map<String, Boolean> disabledByKey = new HashMap<>();
        for (NotificationPreference pref : stored) {
            disabledByKey.put(key(pref.getEvent(), pref.getChannel()), pref.isEnabled());
        }

        Map<String, Map<String, Boolean>> result = new HashMap<>();
        for (NotificationEvent event : NotificationEvent.values()) {
            Map<String, Boolean> channels = new HashMap<>();
            for (NotificationChannel channel : NotificationChannel.values()) {
                channels.put(channel.name(), MandatoryNotificationChannels.cannotBeTurnedOff(event, channel) || disabledByKey.getOrDefault(key(event, channel), true));
            }
            result.put(event.name(), channels);
        }

        return new NotificationPreferencesResponse(result, MandatoryNotificationChannels.channelNamesByEventName());
    }

    private String key(NotificationEvent event, NotificationChannel channel) {
        return event.name() + ":" + channel.name();
    }
}
