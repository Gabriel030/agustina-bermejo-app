package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.NotificationChannel;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.identity.entity.NotificationPreference;
import com.red_escuela.backend.identity.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationPreferenceFinderTest {

    private final NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
    private final NotificationPreferenceFinder finder = new NotificationPreferenceFinder(repository);

    private void stored(NotificationEvent event, NotificationChannel channel, boolean enabled) {
        NotificationPreference preference = new NotificationPreference();
        preference.setEvent(event);
        preference.setChannel(channel);
        preference.setEnabled(enabled);
        when(repository.findByPersonIdAndEventAndChannel(1L, event, channel)).thenReturn(Optional.of(preference));
    }

    @Test
    void withoutStoredPreferenceEverythingIsEnabled() {
        when(repository.findByPersonIdAndEventAndChannel(any(), any(), any())).thenReturn(Optional.empty());

        for (NotificationEvent event : NotificationEvent.values()) {
            for (NotificationChannel channel : NotificationChannel.values()) {
                assertThat(finder.isEnabled(1L, event, channel)).isTrue();
            }
        }
    }

    @Test
    void aStoredOffPreferenceDisablesOnlyThatEventAndChannel() {
        when(repository.findByPersonIdAndEventAndChannel(any(), any(), any())).thenReturn(Optional.empty());
        stored(NotificationEvent.INSTITUTIONAL_NOTICE, NotificationChannel.APP, false);

        assertThat(finder.isInAppEnabled(1L, NotificationEvent.INSTITUTIONAL_NOTICE)).isFalse();
        assertThat(finder.isEnabled(1L, NotificationEvent.INSTITUTIONAL_NOTICE, NotificationChannel.EMAIL)).isTrue();
        assertThat(finder.isInAppEnabled(1L, NotificationEvent.SUBJECT_ENROLLMENT)).isTrue();
    }

    @Test
    void aMandatoryChannelIgnoresAStoredOffPreference() {
        stored(NotificationEvent.ACCOUNT_CREATED, NotificationChannel.EMAIL, false);
        stored(NotificationEvent.PAYMENT_DUE_SOON, NotificationChannel.APP, false);

        assertThat(finder.isEnabled(1L, NotificationEvent.ACCOUNT_CREATED, NotificationChannel.EMAIL)).isTrue();
        assertThat(finder.isInAppEnabled(1L, NotificationEvent.PAYMENT_DUE_SOON)).isTrue();
    }

    @Test
    void thePreDueReminderEmailFollowsItsStoredPreference() {
        stored(NotificationEvent.PAYMENT_DUE_SOON, NotificationChannel.EMAIL, false);

        assertThat(finder.isPreDueReminderEmailEnabled(1L)).isFalse();
    }
}
