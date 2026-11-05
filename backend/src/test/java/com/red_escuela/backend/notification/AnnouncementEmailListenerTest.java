package com.red_escuela.backend.notification;

import com.red_escuela.backend.announcement.events.AnnouncementPublishedEvent;
import com.red_escuela.backend.announcement.events.AnnouncementUpdatedEvent;
import com.red_escuela.backend.identity.api.InstitutionAcademicSettings;
import com.red_escuela.backend.identity.api.InstitutionAcademicSettingsReader;
import com.red_escuela.backend.identity.api.NotificationPreferenceFinder;
import com.red_escuela.backend.identity.entity.NotificationChannel;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.service.DeliveryRequest;
import com.red_escuela.backend.notification.delivery.service.NotificationDeliveryService;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnouncementEmailListenerTest {
    private static final Instant OCCURRED_AT = Instant.parse("2027-03-13T22:00:00Z");

    private final ProcessedEffectGuard guard = mock(ProcessedEffectGuard.class);
    private final NotificationDeliveryService deliveryService = mock(NotificationDeliveryService.class);
    private final NotificationPreferenceFinder preferences = mock(NotificationPreferenceFinder.class);
    private final InstitutionAcademicSettingsReader settings = mock(InstitutionAcademicSettingsReader.class);
    private final Clock clock = Clock.fixed(OCCURRED_AT, ZoneOffset.UTC);
    private final AnnouncementEmailListener listener = new AnnouncementEmailListener(guard, deliveryService, preferences, settings, clock);

    @BeforeEach
    void runGuardedEffects() {
        when(guard.runEffectOnce(any(DeduplicableDomainEvent.class), any(), any())).thenAnswer(call -> {
            ((Runnable) call.getArgument(2)).run();
            return true;
        });
        when(settings.read()).thenReturn(new InstitutionAcademicSettings(5, 15, false));
        when(preferences.isEnabled(any(), any(), any())).thenReturn(true);
    }

    private AnnouncementPublishedEvent published(boolean urgent, Long... personIds) {
        return new AnnouncementPublishedEvent(CorrelationIds.current(), OCCURRED_AT, 70L, "Algebra", List.of(personIds), urgent, "Suspension de clases");
    }

    private List<DeliveryRequest> enqueued(int expected) {
        ArgumentCaptor<DeliveryRequest> captor = ArgumentCaptor.forClass(DeliveryRequest.class);
        verify(deliveryService, times(expected)).enqueue(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void urgentAnnouncementEnqueuesEmailPerRecipient() {
        listener.emailAnnouncementPublished(published(true, 1L, 2L));

        List<DeliveryRequest> requests = enqueued(2);
        assertThat(requests).extracting(request -> request.envelope().recipientPersonId()).containsExactly(1L, 2L);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.sendsEmail()).isTrue();
            assertThat(request.sendsInApp()).isFalse();
            assertThat(request.email().subject()).isEqualTo("Aviso urgente: Suspension de clases");
            assertThat(request.envelope().category()).isEqualTo(DeliveryCategory.ANNOUNCEMENT);
            assertThat(request.envelope().scheduledFor()).isEqualTo(LocalDate.of(2027, 3, 13));
            assertThat(request.emailPrecedesDueDate()).isFalse();
        });
        assertThat(requests.get(0).envelope().dedupKey()).isEqualTo("announcement:70:1:PUBLISHED");
    }

    @Test
    void urgentAnnouncementIgnoresTheSwitchAndThePreference() {
        when(preferences.isEnabled(any(), any(), any())).thenReturn(false);

        listener.emailAnnouncementPublished(published(true, 1L));

        enqueued(1);
        verify(preferences, never()).isEnabled(any(), any(), any());
        verify(settings, never()).read();
    }

    @Test
    void nonUrgentSkippedWhenSwitchOff() {
        listener.emailAnnouncementPublished(published(false, 1L, 2L));

        verify(deliveryService, never()).enqueue(any());
    }

    @Test
    void nonUrgentSentWhenSwitchOn() {
        when(settings.read()).thenReturn(new InstitutionAcademicSettings(5, 15, true));

        listener.emailAnnouncementPublished(published(false, 1L));

        DeliveryRequest request = enqueued(1).get(0);
        assertThat(request.email().subject()).isEqualTo("Aviso: Suspension de clases");
    }

    @Test
    void nonUrgentRespectsEmailPreference() {
        when(settings.read()).thenReturn(new InstitutionAcademicSettings(5, 15, true));
        when(preferences.isEnabled(2L, NotificationEvent.INSTITUTIONAL_NOTICE, NotificationChannel.EMAIL)).thenReturn(false);

        listener.emailAnnouncementPublished(published(false, 1L, 2L, 3L));

        assertThat(enqueued(2)).extracting(request -> request.envelope().recipientPersonId()).containsExactly(1L, 3L);
    }

    @Test
    void updatedAnnouncementUsesItsOwnKindAndHandler() {
        AnnouncementUpdatedEvent event = new AnnouncementUpdatedEvent(CorrelationIds.current(), OCCURRED_AT, 70L, "Algebra", List.of(1L), true, "Cambio de aula");

        listener.emailAnnouncementUpdated(event);

        DeliveryRequest request = enqueued(1).get(0);
        assertThat(request.envelope().dedupKey()).isEqualTo("announcement:70:1:UPDATED");
        assertThat(request.envelope().kind()).isEqualTo("ANNOUNCEMENT_UPDATED");
        verify(guard).runEffectOnce(any(DeduplicableDomainEvent.class), eq(AnnouncementEmailListener.UPDATED_HANDLER), any());
    }

    @Test
    void aVeryLongTitleIsTruncatedInTheSubject() {
        AnnouncementPublishedEvent event = new AnnouncementPublishedEvent(CorrelationIds.current(), OCCURRED_AT, 70L, "Algebra", List.of(1L), true, "t".repeat(500));

        listener.emailAnnouncementPublished(event);

        assertThat(enqueued(1).get(0).email().subject()).hasSize(300);
    }
}
