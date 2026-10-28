package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.collections.domain.MissedReminderRecovery;
import com.red_escuela.backend.billing.collections.repository.CollectionReminderRunStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MissedReminderDaysFinderTest {

    private final CollectionReminderRunStore runStore = mock(CollectionReminderRunStore.class);
    private final Set<LocalDate> holidays = new java.util.HashSet<>();
    private final BusinessDayCalendar calendar = day -> day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(day);
    private final MissedReminderDaysFinder finder = new MissedReminderDaysFinder(runStore, calendar);

    private static LocalDate march(int day) {
        return LocalDate.of(2027, 3, day);
    }

    private void ranOn(LocalDate firstRun, LocalDate... days) {
        when(runStore.findEarliestRunDate()).thenReturn(Optional.of(firstRun));
        when(runStore.findRunDatesBetween(any(), any())).thenReturn(List.of(days));
    }

    @Test
    @Tag("D-436")
    void withoutAnyRecordedRunNothingIsRecovered() {
        when(runStore.findEarliestRunDate()).thenReturn(Optional.empty());

        assertThat(finder.findRecoveryBefore(march(23)).isRecovering()).isFalse();
    }

    @Test
    @Tag("D-436")
    void aSingleMissedBusinessDayIsRecoveredTogetherWithTheWeekendBefore() {
        ranOn(march(1), march(18), march(19));

        assertThat(finder.findRecoveryBefore(march(23)).missedDays()).containsExactlyInAnyOrder(march(20), march(21), march(22));
    }

    @Test
    @Tag("D-436")
    void theLookBackNeverExceedsThreeBusinessDays() {
        ranOn(march(1));

        MissedReminderRecovery recovery = finder.findRecoveryBefore(march(12));

        assertThat(recovery.missedDays()).containsExactlyInAnyOrder(march(9), march(10), march(11));
    }

    @Test
    @Tag("D-436")
    void aHolidayDoesNotCountAsABusinessDayOfTheLookBack() {
        holidays.add(march(11));
        ranOn(march(1));

        MissedReminderRecovery recovery = finder.findRecoveryBefore(march(12));

        assertThat(recovery.missedDays()).containsExactlyInAnyOrder(march(8), march(9), march(10), march(11));
    }

    @Test
    @Tag("D-436")
    void daysBeforeTheFirstRecordedRunAreNeverRecovered() {
        ranOn(march(11));

        assertThat(finder.findRecoveryBefore(march(12)).missedDays()).isEmpty();
        assertThat(finder.findRecoveryBefore(march(15)).missedDays()).containsExactlyInAnyOrder(march(12), march(13), march(14));
    }

    @Test
    @Tag("D-437")
    void aDayWithARecordedRunIsNotMissed() {
        ranOn(march(1), march(9), march(10), march(11));

        assertThat(finder.findRecoveryBefore(march(12)).isRecovering()).isFalse();
    }
}
