package com.red_escuela.backend.notification;

import com.red_escuela.backend.academicstructure.events.HolidaysMissingForNextYearEvent;
import com.red_escuela.backend.identity.api.PersonRecipientFinder;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HolidaysMissingNotificationListenerTest {

    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final HolidaysMissingNotificationListener listener = new HolidaysMissingNotificationListener(effectGuard, eventPublisher);

    @Test
    void theReminderIsAddressedOnlyToAdministrativeStaffAndNamesTheYear() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return true;
        }).when(effectGuard).runEffectOnce(any(), any(), any());

        listener.notifyAdministrative(HolidaysMissingForNextYearEvent.missingFor(2027, 0));

        ArgumentCaptor<BillingOperationNotification> published = ArgumentCaptor.forClass(BillingOperationNotification.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().type()).isEqualTo(NotificationType.HOLIDAYS_MISSING_FOR_NEXT_YEAR);
        assertThat(published.getValue().message()).contains("2027");
        assertThat(published.getValue().recipients()).containsExactly(NotificationTopic.HOLIDAY_CALENDAR_ADMINISTRATION);
    }

    @Test
    void theTopicResolvesToThePeopleWithTheAdministrativeRoleOnly() {
        PersonRecipientFinder finder = mock(PersonRecipientFinder.class);
        Person administrative = new Person();
        administrative.setId(1L);
        when(finder.findByRoleName("ADMINISTRATIVO")).thenReturn(List.of(administrative));

        assertThat(NotificationTopic.HOLIDAY_CALENDAR_ADMINISTRATION.resolve(finder)).containsExactly(administrative);
        verify(finder).findByRoleName("ADMINISTRATIVO");
        org.mockito.Mockito.verifyNoMoreInteractions(finder);
    }

    @Test
    void theReminderAppearsInTheUserPreferencesMatrix() {
        assertThat(UserPreferenceEventResolver.resolve(NotificationType.HOLIDAYS_MISSING_FOR_NEXT_YEAR)).contains(NotificationEvent.HOLIDAY_CALENDAR_REMINDER);
    }
}
