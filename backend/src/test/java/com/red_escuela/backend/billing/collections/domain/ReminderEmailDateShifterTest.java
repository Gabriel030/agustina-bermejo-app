package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderEmailDateShifterTest {
    private final NonBusinessDays weekendsOnly = NonBusinessDays.weekendsOnly();

    private LocalDate shift(String noticeDate, String dueDate, NonBusinessDays days) {
        return ReminderEmailDateShifter.shiftEmailDate(LocalDate.parse(noticeDate), LocalDate.parse(dueDate), days);
    }

    @Test
    @Tag("D-342")
    void emailOnABusinessDayDoesNotMove() {
        assertThat(shift("2026-03-09", "2026-03-11", weekendsOnly)).isEqualTo(LocalDate.parse("2026-03-09"));
    }

    @Test
    @Tag("D-342")
    void emailOnSaturdayMovesToMonday() {
        assertThat(shift("2026-03-14", "2026-03-31", weekendsOnly)).isEqualTo(LocalDate.parse("2026-03-16"));
        assertThat(shift("2026-03-15", "2026-03-31", weekendsOnly)).isEqualTo(LocalDate.parse("2026-03-16"));
    }

    @Test
    @Tag("D-342")
    void emailOnAHolidayMovesToTheNextBusinessDay() {
        NonBusinessDays withHoliday = new NonBusinessDays(Set.of(LocalDate.parse("2026-03-24")));

        assertThat(shift("2026-03-24", "2026-04-30", withHoliday)).isEqualTo(LocalDate.parse("2026-03-25"));
    }

    @Test
    @Tag("D-342")
    void emailMovingPastTheDueDateGoesBackToThePreviousBusinessDay() {
        assertThat(shift("2026-04-12", "2026-04-13", weekendsOnly)).as("without holiday the mail goes out on the due date").isEqualTo(LocalDate.parse("2026-04-13"));

        NonBusinessDays holidayOnTheDueDate = new NonBusinessDays(Set.of(LocalDate.parse("2026-04-13")));
        assertThat(shift("2026-04-11", "2026-04-13", holidayOnTheDueDate)).isEqualTo(LocalDate.parse("2026-04-10"));
        assertThat(shift("2026-04-12", "2026-04-13", holidayOnTheDueDate)).isEqualTo(LocalDate.parse("2026-04-10"));
    }

    @Test
    @Tag("D-342")
    void aWeekendDueDateKeepsThePreDueMailBeforeIt() {
        assertThat(shift("2026-03-14", "2026-03-14", weekendsOnly)).isEqualTo(LocalDate.parse("2026-03-13"));
    }

    @Test
    @Tag("D-342")
    void anAfterDueEmailOnAWeekendStillMovesForward() {
        assertThat(shift("2026-04-11", "2026-04-01", weekendsOnly)).isEqualTo(LocalDate.parse("2026-04-13"));
    }

    @Test
    @Tag("D-342")
    void inAppNeverMoves() {
        ReminderOccurrence saturday = new ReminderOccurrence(1L, "a", ReminderMessageType.BEFORE_DUE_REMINDER, EnumSet.of(ReminderChannel.IN_APP, ReminderChannel.EMAIL),
                LocalDate.parse("2026-03-14"), LocalDate.parse("2026-03-31"));

        assertThat(saturday.inAppDate()).contains(LocalDate.parse("2026-03-14"));
        assertThat(saturday.emailDate(weekendsOnly)).contains(LocalDate.parse("2026-03-16"));
    }

    @Test
    @Tag("D-372")
    @Tag("D-342")
    void emailPrecedesTheDueDateJudgedByTheShiftedSendDateAndInAppByTheNoticeDate() {
        ReminderOccurrence saturdayNotice = new ReminderOccurrence(1L, "a", ReminderMessageType.BEFORE_DUE_REMINDER, EnumSet.of(ReminderChannel.IN_APP, ReminderChannel.EMAIL),
                LocalDate.parse("2026-04-11"), LocalDate.parse("2026-04-13"));
        NonBusinessDays holidayOnTheDueDate = new NonBusinessDays(Set.of(LocalDate.parse("2026-04-13")));

        assertThat(saturdayNotice.inAppPrecedesDueDate()).isTrue();
        assertThat(saturdayNotice.emailPrecedesDueDate(weekendsOnly)).isFalse();
        assertThat(saturdayNotice.emailPrecedesDueDate(holidayOnTheDueDate)).isTrue();
    }

    @Test
    @Tag("D-342")
    void anOccurrenceWithoutEmailHasNoEmailDateAndOneWithoutInAppHasNoInAppDate() {
        ReminderOccurrence inAppOnly = new ReminderOccurrence(1L, "a", ReminderMessageType.ISSUANCE_NOTICE, EnumSet.of(ReminderChannel.IN_APP),
                LocalDate.parse("2026-03-14"), LocalDate.parse("2026-03-31"));
        ReminderOccurrence emailOnly = new ReminderOccurrence(1L, "b", ReminderMessageType.ISSUANCE_NOTICE, EnumSet.of(ReminderChannel.EMAIL),
                LocalDate.parse("2026-03-14"), LocalDate.parse("2026-03-31"));

        assertThat(inAppOnly.emailDate(weekendsOnly)).isEmpty();
        assertThat(emailOnly.inAppDate()).isEmpty();
    }
}
