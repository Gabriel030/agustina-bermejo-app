package com.red_escuela.backend.billing.configuration.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderMilestoneDatesTest {
    private final ReminderMilestoneDates withoutSuspension = ReminderMilestoneDates.withoutSuspension(LocalDate.parse("2027-03-01"), LocalDate.parse("2027-03-11"));

    @Test
    @Tag("D-368")
    void aMilestoneDatesRecordWithoutSuspensionDateIsValid() {
        assertThat(withoutSuspension.dateOf(ReminderMilestone.SUSPENSION)).isEmpty();
        assertThat(withoutSuspension.dateOf(ReminderMilestone.ISSUANCE)).contains(LocalDate.parse("2027-03-01"));
        assertThat(withoutSuspension.dateOf(ReminderMilestone.DUE_DATE)).contains(LocalDate.parse("2027-03-11"));
    }

    @Test
    @Tag("D-368")
    void aNoticeAnchoredToTheSuspensionWithoutDateHasNoFirstDateAndIsNotBeforeTheDueDate() {
        ReminderNotice beforeSuspension = ReminderNotice.of("a", ReminderMessageType.BEFORE_SUSPENSION_REMINDER, ReminderMilestone.SUSPENSION, -1, EnumSet.of(ReminderChannel.EMAIL));

        assertThat(beforeSuspension.firstDateFor(withoutSuspension)).isEmpty();
        assertThat(beforeSuspension.isBeforeDueDate(withoutSuspension)).isFalse();
        assertThat(beforeSuspension.occurrenceDatesFor(withoutSuspension)).isEmpty();
    }

    @Test
    @Tag("D-368")
    void aNoticeSendsEmailOnlyWhenItsChannelsIncludeEmail() {
        ReminderNotice inAppOnly = ReminderNotice.of("a", ReminderMessageType.ISSUANCE_NOTICE, ReminderMilestone.ISSUANCE, 0, EnumSet.of(ReminderChannel.IN_APP));
        ReminderNotice both = ReminderNotice.of("b", ReminderMessageType.ISSUANCE_NOTICE, ReminderMilestone.ISSUANCE, 0, EnumSet.of(ReminderChannel.IN_APP, ReminderChannel.EMAIL));

        assertThat(inAppOnly.sendsEmail()).isFalse();
        assertThat(both.sendsEmail()).isTrue();
    }

    @Test
    @Tag("D-368")
    void occurrenceDatesStopBeforeTheUntilMilestone() {
        ReminderNotice repeating = new ReminderNotice("a", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1, EnumSet.of(ReminderChannel.EMAIL),
                new ReminderRepetition(3, ReminderMilestone.SUSPENSION));
        ReminderMilestoneDates withSuspension = new ReminderMilestoneDates(LocalDate.parse("2027-03-01"), LocalDate.parse("2027-03-11"), LocalDate.parse("2027-03-22"));

        List<LocalDate> dates = repeating.occurrenceDatesFor(withSuspension);

        assertThat(dates).containsExactly(LocalDate.parse("2027-03-12"), LocalDate.parse("2027-03-15"), LocalDate.parse("2027-03-18"), LocalDate.parse("2027-03-21"));
    }
}
