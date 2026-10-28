package com.red_escuela.backend.notification.delivery.service;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchSettings;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchWindow;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("c06")
class EmailDeliveryDispatchJobTest {
    private static final LocalDate TUESDAY = LocalDate.of(2027, 3, 9);

    private final AtomicReference<Instant> now = new AtomicReference<>();
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private NotificationDeliveryRepository repository;
    private EmailDeliveryChannel channel;
    private EmailDispatchPlanningService planning;
    private EmailDispatchSettingsSource settings;
    private BusinessDayCalendar calendar;
    private EmailDeliveryDispatchJob job;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationDeliveryRepository.class);
        channel = mock(EmailDeliveryChannel.class);
        calendar = mock(BusinessDayCalendar.class);
        planning = mock(EmailDispatchPlanningService.class);
        settings = mock(EmailDispatchSettingsSource.class);
        when(settings.settingsEffectiveOn(any())).thenReturn(new EmailDispatchSettings(EmailDispatchWindow.STANDARD, 250));
        when(calendar.isBusinessDay(TUESDAY)).thenReturn(true);
        when(calendar.isBusinessDay(TUESDAY.plusDays(1))).thenReturn(true);
        now.set(Instant.parse("2027-03-09T10:00:00Z"));
        job = new EmailDeliveryDispatchJob(repository, planning, settings, channel, calendar, clock);
    }

    private void idsAreReturnedOnce(Long... ids) {
        when(planning.planAndApplyFor(any(), any(), any())).thenReturn(List.of(ids));
    }

    @Test
    void everyDueDeliveryIsDispatched() {
        idsAreReturnedOnce(1L, 2L, 3L);

        int attempted = job.dispatchDueEmails();

        assertThat(attempted).isEqualTo(3);
        verify(channel).dispatchDelivery(1L);
        verify(channel).dispatchDelivery(2L);
        verify(channel).dispatchDelivery(3L);
    }

    @Test
    void theRunStopsWhenTheWindowClosesInTheMiddleOfTheBatch() {
        idsAreReturnedOnce(1L, 2L);
        doAnswer(invocation -> {
            now.set(Instant.parse("2027-03-09T11:00:00Z"));
            return null;
        }).when(channel).dispatchDelivery(1L);

        int attempted = job.dispatchDueEmails();

        assertThat(attempted).isEqualTo(1);
        verify(channel, never()).dispatchDelivery(2L);
    }

    @Test
    void aFailureOnOneDeliveryDoesNotStopTheOthers() {
        idsAreReturnedOnce(1L, 2L);
        doThrow(new IllegalStateException("fallo")).when(channel).dispatchDelivery(1L);

        int attempted = job.dispatchDueEmails();

        assertThat(attempted).isEqualTo(2);
        verify(channel).dispatchDelivery(2L);
    }

    @Test
    void aNonBusinessDayDoesNotEvenQueryTheDeliveries() {
        when(calendar.isBusinessDay(TUESDAY)).thenReturn(false);

        int attempted = job.dispatchDueEmails();

        assertThat(attempted).isZero();
        verify(planning, never()).planAndApplyFor(any(), any(), any());
        verify(channel, never()).dispatchDelivery(anyLong());
    }

}
