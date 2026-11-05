package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.events.HolidaysMissingForNextYearEvent;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HolidaysMissingForNextYearReminderJobTest {

    private static final LocalDate WINDOW_OPENS = LocalDate.of(2026, 11, 2);

    private final HolidayRepository holidayRepository = mock(HolidayRepository.class);
    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final Set<UUID> claimedEffects = new HashSet<>();
    private HolidaysMissingForNextYearReminderJob job;

    @BeforeEach
    void wireGuardThatClaimsEachEventOnlyOnce() {
        doAnswer(invocation -> {
            DeduplicableDomainEvent event = invocation.getArgument(0);
            if (!claimedEffects.add(event.eventId())) {
                return false;
            }
            ((Runnable) invocation.getArgument(2)).run();
            return true;
        }).when(effectGuard).runEffectOnce(any(), eq(HolidaysMissingForNextYearReminderJob.REMINDER_HANDLER), any());
        job = jobAt(WINDOW_OPENS);
    }

    private HolidaysMissingForNextYearReminderJob jobAt(LocalDate today) {
        Clock clock = Clock.fixed(today.atTime(8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new HolidaysMissingForNextYearReminderJob(holidayRepository, effectGuard, eventPublisher, clock);
    }

    private void nextYearHolidaysLoaded(boolean loaded) {
        when(holidayRepository.existsByDateBetween(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31))).thenReturn(loaded);
    }

    @Test
    void sixtyDaysBeforeNewYearWithoutNextYearHolidaysEmitsTheReminderForThatYear() {
        nextYearHolidaysLoaded(false);

        assertThat(job.remindIfMissing(WINDOW_OPENS)).isTrue();

        ArgumentCaptor<HolidaysMissingForNextYearEvent> published = ArgumentCaptor.forClass(HolidaysMissingForNextYearEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().year()).isEqualTo(2027);
        assertThat(published.getValue().weeksSinceWindowOpened()).isZero();
    }

    @Test
    void sixtyOneDaysBeforeNewYearNothingIsEmitted() {
        nextYearHolidaysLoaded(false);

        assertThat(job.remindIfMissing(WINDOW_OPENS.minusDays(1))).isFalse();

        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(holidayRepository, never()).existsByDateBetween(any(), any());
    }

    @Test
    void runningTheJobAgainTheSameWeekOrAfterARestartDoesNotDuplicateTheReminder() {
        nextYearHolidaysLoaded(false);

        assertThat(job.remindIfMissing(WINDOW_OPENS)).isTrue();
        assertThat(job.remindIfMissing(WINDOW_OPENS)).isFalse();
        assertThat(job.remindIfMissing(WINDOW_OPENS.plusDays(6))).isFalse();
        assertThat(jobAt(WINDOW_OPENS.plusDays(3)).remindIfMissing(WINDOW_OPENS.plusDays(3))).isFalse();

        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void theWeekAfterTheReminderRepeatsWithADistinctIdentity() {
        nextYearHolidaysLoaded(false);

        job.remindIfMissing(WINDOW_OPENS);
        assertThat(job.remindIfMissing(WINDOW_OPENS.plusDays(7))).isTrue();

        ArgumentCaptor<HolidaysMissingForNextYearEvent> published = ArgumentCaptor.forClass(HolidaysMissingForNextYearEvent.class);
        verify(eventPublisher, times(2)).publishEvent(published.capture());
        assertThat(published.getAllValues()).extracting(HolidaysMissingForNextYearEvent::weeksSinceWindowOpened).containsExactly(0, 1);
        assertThat(published.getAllValues()).extracting(HolidaysMissingForNextYearEvent::eventId).doesNotHaveDuplicates();
    }

    @Test
    void onceAHolidayOfNextYearIsLoadedTheReminderStops() {
        nextYearHolidaysLoaded(false);
        job.remindIfMissing(WINDOW_OPENS);

        nextYearHolidaysLoaded(true);

        assertThat(job.remindIfMissing(WINDOW_OPENS.plusDays(7))).isFalse();
        assertThat(job.remindIfMissing(LocalDate.of(2026, 12, 31))).isFalse();
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void theReminderKeepsComingUntilTheLastDayOfTheYear() {
        nextYearHolidaysLoaded(false);

        assertThat(job.remindIfMissing(LocalDate.of(2026, 12, 31))).isTrue();
    }

    @Test
    void onNewYearsDayTheWindowForTheFollowingYearIsNotOpenYet() {
        when(holidayRepository.existsByDateBetween(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31))).thenReturn(false);

        assertThat(job.remindIfMissing(LocalDate.of(2027, 1, 1))).isFalse();
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void theScheduledEntryPointUsesTheInjectedClock() {
        nextYearHolidaysLoaded(false);

        jobAt(WINDOW_OPENS).runDaily();

        verify(eventPublisher).publishEvent(any(HolidaysMissingForNextYearEvent.class));
    }
}
