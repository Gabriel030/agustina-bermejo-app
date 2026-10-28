package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.dto.NotificationPreferencesResponse;
import com.red_escuela.backend.identity.dto.UpdateNotificationPreferencesRequest;
import com.red_escuela.backend.identity.entity.NotificationChannel;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.identity.entity.NotificationPreference;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.NotificationPreferenceRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.identity.domain.MandatoryNotificationChannels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-340")
class NotificationPreferencesServicesTest {
    private final NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
    private final CurrentPersonService currentPerson = mock(CurrentPersonService.class);
    private final Person person = new Person();
    private final GetNotificationPreferencesService getService = new GetNotificationPreferencesService(repository, currentPerson);
    private final UpdateNotificationPreferencesService updateService = new UpdateNotificationPreferencesService(repository, currentPerson);

    private NotificationPreference storedOff(NotificationEvent event, NotificationChannel channel) {
        NotificationPreference preference = new NotificationPreference();
        preference.setPerson(person);
        preference.setEvent(event);
        preference.setChannel(channel);
        preference.setEnabled(false);
        return preference;
    }

    @Test
    void onlyTheInAppChannelOfTheDueSoonReminderCannotBeTurnedOff() {
        for (NotificationEvent event : NotificationEvent.values()) {
            for (NotificationChannel channel : NotificationChannel.values()) {
                boolean expected = event == NotificationEvent.PAYMENT_DUE_SOON && channel == NotificationChannel.APP;
                assertThat(MandatoryNotificationChannels.cannotBeTurnedOff(event, channel)).as(event + "/" + channel).isEqualTo(expected);
            }
        }
    }

    @Test
    void theResponseListsTheMandatoryChannelsOfEachEventAndShowsThemAlwaysOnEvenIfAnOldPreferenceTurnedThemOff() {
        when(currentPerson.resolveCurrentPerson()).thenReturn(person);
        when(repository.findByPerson(person)).thenReturn(List.of(storedOff(NotificationEvent.PAYMENT_DUE_SOON, NotificationChannel.APP),
                storedOff(NotificationEvent.PAYMENT_DUE_SOON, NotificationChannel.EMAIL)));

        NotificationPreferencesResponse response = getService.getPreferences();

        assertThat(response.mandatoryChannels().get("PAYMENT_DUE_SOON")).containsExactly("APP");
        assertThat(response.mandatoryChannels().get("GRADE_POSTED")).isEmpty();
        assertThat(response.mandatoryChannels()).hasSameSizeAs(NotificationEvent.values());
        assertThat(response.preferences().get("PAYMENT_DUE_SOON")).containsEntry("APP", true).containsEntry("EMAIL", false);
        assertThat(response.preferences().get("GRADE_POSTED")).containsEntry("APP", true).containsEntry("EMAIL", true);
    }

    @Test
    void turningOffAMandatoryChannelIsIgnoredWhileTheOptionalOneIsStored() {
        when(currentPerson.resolveCurrentPerson()).thenReturn(person);
        when(repository.findByPersonAndEventAndChannel(any(), any(), any())).thenReturn(Optional.empty());

        updateService.updatePreferences(new UpdateNotificationPreferencesRequest(Map.of("PAYMENT_DUE_SOON", Map.of("APP", false, "EMAIL", false))));

        ArgumentCaptor<NotificationPreference> saved = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(saved.getValue().getEvent()).isEqualTo(NotificationEvent.PAYMENT_DUE_SOON);
        assertThat(saved.getValue().isEnabled()).isFalse();
    }

    @Test
    void turningOnAMandatoryChannelStillClearsALegacyPreference() {
        when(currentPerson.resolveCurrentPerson()).thenReturn(person);
        NotificationPreference legacy = storedOff(NotificationEvent.PAYMENT_DUE_SOON, NotificationChannel.APP);
        when(repository.findByPersonAndEventAndChannel(person, NotificationEvent.PAYMENT_DUE_SOON, NotificationChannel.APP)).thenReturn(Optional.of(legacy));

        updateService.updatePreferences(new UpdateNotificationPreferencesRequest(Map.of("PAYMENT_DUE_SOON", Map.of("APP", true))));

        verify(repository).delete(legacy);
        verify(repository, never()).save(any());
    }
}
