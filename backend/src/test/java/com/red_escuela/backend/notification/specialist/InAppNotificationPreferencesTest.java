package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.notification.domain.DomainNotificationEvent;
import com.red_escuela.backend.notification.domain.BillingOperationNotification;
import com.red_escuela.backend.identity.api.NotificationPreferenceFinder;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InAppNotificationPreferencesTest {

    private static final Set<NotificationType> USER_CONFIGURABLE = EnumSet.of(
            NotificationType.ANNOUNCEMENT_PUBLISHED, NotificationType.ANNOUNCEMENT_UPDATED,
            NotificationType.EXAM_BOARD_RESCHEDULED, NotificationType.EXAM_BOARD_CANCELED,
            NotificationType.COURSE_WAITLIST_EXPIRED, NotificationType.PROGRAM_APPLICATION_APPROVED,
            NotificationType.COLLECTION_REMINDER, NotificationType.HOLIDAYS_MISSING_FOR_NEXT_YEAR);

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationPreferenceFinder finder = mock(NotificationPreferenceFinder.class);
    private final InAppNotificationChannel channel = new InAppNotificationChannel(repository, finder);
    private final Person person = personWithId(7L);

    private static Person personWithId(Long id) {
        Person person = new Person();
        person.setId(id);
        return person;
    }

    private static DomainNotificationEvent eventOf(NotificationType type) {
        return new BillingOperationNotification(type, "message", List.of(), "Entity", 1L);
    }

    @Test
    void exactlyTheUserFacingTypesAreMappedToAPreferenceEvent() {
        Set<NotificationType> mapped = EnumSet.noneOf(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            if (UserPreferenceEventResolver.resolve(type).isPresent()) {
                mapped.add(type);
            }
        }

        assertThat(mapped).isEqualTo(USER_CONFIGURABLE);
    }

    @Test
    void announcementsEnrollmentsAndRemindersMapToTheirMatrixEvent() {
        assertThat(UserPreferenceEventResolver.resolve(NotificationType.ANNOUNCEMENT_PUBLISHED)).contains(NotificationEvent.INSTITUTIONAL_NOTICE);
        assertThat(UserPreferenceEventResolver.resolve(NotificationType.ANNOUNCEMENT_UPDATED)).contains(NotificationEvent.INSTITUTIONAL_NOTICE);
        assertThat(UserPreferenceEventResolver.resolve(NotificationType.EXAM_BOARD_RESCHEDULED)).contains(NotificationEvent.EXAM_BOARD_REGISTERED);
        assertThat(UserPreferenceEventResolver.resolve(NotificationType.EXAM_BOARD_CANCELED)).contains(NotificationEvent.EXAM_BOARD_REGISTERED);
        assertThat(UserPreferenceEventResolver.resolve(NotificationType.COURSE_WAITLIST_EXPIRED)).contains(NotificationEvent.SUBJECT_ENROLLMENT);
        assertThat(UserPreferenceEventResolver.resolve(NotificationType.PROGRAM_APPLICATION_APPROVED)).contains(NotificationEvent.SUBJECT_ENROLLMENT);
        assertThat(UserPreferenceEventResolver.resolve(NotificationType.COLLECTION_REMINDER)).contains(NotificationEvent.PAYMENT_DUE_SOON);
    }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void withTheInAppPreferenceOffOnlyTheConfigurableTypesAreSuppressed(NotificationType type) {
        when(finder.isInAppEnabled(anyLong(), any())).thenReturn(false);

        channel.send(person, eventOf(type));

        if (USER_CONFIGURABLE.contains(type)) {
            verify(repository, never()).save(any());
        } else {
            verify(repository).save(any());
        }
    }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void withTheInAppPreferenceOnEveryTypeIsStored(NotificationType type) {
        when(finder.isInAppEnabled(anyLong(), any())).thenReturn(true);

        channel.send(person, eventOf(type));

        verify(repository).save(any());
    }

    @Test
    void thePreferenceIsAskedForTheRecipientAndTheMatrixEventOfTheType() {
        when(finder.isInAppEnabled(eq(7L), eq(NotificationEvent.INSTITUTIONAL_NOTICE))).thenReturn(false);
        when(finder.isInAppEnabled(eq(8L), eq(NotificationEvent.INSTITUTIONAL_NOTICE))).thenReturn(true);

        channel.send(person, eventOf(NotificationType.ANNOUNCEMENT_PUBLISHED));
        channel.send(personWithId(8L), eventOf(NotificationType.ANNOUNCEMENT_PUBLISHED));

        verify(repository).save(any());
    }
}
