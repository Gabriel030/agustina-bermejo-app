package com.red_escuela.backend.billing.configuration.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderScheduleResolverTest {
    private static final CollectionReminderSettings GLOBAL = CollectionReminderSettings.initial();
    private static final ReminderMilestoneDates MARCH = new ReminderMilestoneDates(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 31));

    private static ReminderScheduleVersion version(long id, ReminderSchedule schedule) {
        return new ReminderScheduleVersion(id, schedule);
    }

    private static ReminderSchedule withNotices(int count, ReminderSendingOverrides overrides) {
        List<ReminderNotice> notices = ReminderSchedule.initial().notices().subList(0, count);
        return new ReminderSchedule(false, notices, overrides);
    }

    @Test
    @Tag("D-368")
    void anActiveExceptionWinsOverTheGroupAndTheGroupOverTheTemplate() {
        ReminderScheduleVersion exception = version(7, withNotices(4, ReminderSendingOverrides.none()));
        ReminderScheduleVersion group = version(3, withNotices(5, ReminderSendingOverrides.none()));

        ResolvedReminderSchedule viaException = ReminderScheduleResolver.resolve(Optional.of(exception), Optional.of(group), GLOBAL, null, Optional.empty());
        ResolvedReminderSchedule viaGroup = ReminderScheduleResolver.resolve(Optional.empty(), Optional.of(group), GLOBAL, null, Optional.empty());

        assertThat(viaException.source()).isEqualTo(ReminderScheduleSource.EXCEPTION);
        assertThat(viaException.configurationVersionId()).isEqualTo(7L);
        assertThat(viaException.notices()).hasSize(4);
        assertThat(viaGroup.source()).isEqualTo(ReminderScheduleSource.GROUP);
        assertThat(viaGroup.configurationVersionId()).isEqualTo(3L);
        assertThat(viaGroup.notices()).hasSize(5);
    }

    @Test
    @Tag("D-368")
    @Tag("D-370")
    void withoutAnyVersionTheTemplateOfFiveNoticesAppliesWithTheGlobalSettings() {
        ResolvedReminderSchedule resolved = ReminderScheduleResolver.resolve(Optional.empty(), Optional.empty(), GLOBAL, null, Optional.empty());

        assertThat(resolved.source()).isEqualTo(ReminderScheduleSource.TEMPLATE);
        assertThat(resolved.configurationVersionId()).isNull();
        assertThat(resolved.notices()).hasSize(5);
        assertThat(resolved.settings()).isEqualTo(GLOBAL);
        assertThat(resolved.trimmedNotices()).isEmpty();
    }

    @Test
    @Tag("D-368")
    void aRemovedExceptionFallsBackToTheGroupAndToTheTemplateWhenThereIsNoGroup() {
        ReminderScheduleVersion removed = version(9, ReminderSchedule.followingGroup());
        ReminderScheduleVersion group = version(3, withNotices(5, ReminderSendingOverrides.none()));

        assertThat(ReminderScheduleResolver.resolve(Optional.of(removed), Optional.of(group), GLOBAL, null, Optional.empty()).source()).isEqualTo(ReminderScheduleSource.GROUP);
        assertThat(ReminderScheduleResolver.resolve(Optional.of(removed), Optional.empty(), GLOBAL, null, Optional.empty()).source()).isEqualTo(ReminderScheduleSource.TEMPLATE);
    }

    @Test
    @Tag("D-369")
    void settingsResolveFieldByFieldFromExceptionThenGroupThenGlobal() {
        ReminderSendingOverrides groupOverrides = new ReminderSendingOverrides(null, null, null, null, null, null, 200, 7, null);
        ReminderSendingOverrides exceptionOverrides = new ReminderSendingOverrides(false, null, null, null, LocalTime.of(10, 0), LocalTime.of(12, 0), null, 21, null);
        ReminderScheduleVersion group = version(3, withNotices(5, groupOverrides));
        ReminderScheduleVersion exception = version(7, withNotices(4, exceptionOverrides));

        CollectionReminderSettings viaException = ReminderScheduleResolver.resolve(Optional.of(exception), Optional.of(group), GLOBAL, null, Optional.empty()).settings();
        CollectionReminderSettings viaGroup = ReminderScheduleResolver.resolve(Optional.empty(), Optional.of(group), GLOBAL, null, Optional.empty()).settings();

        assertThat(viaException).isEqualTo(new CollectionReminderSettings(false, 6, 3, 4, LocalTime.of(10, 0), LocalTime.of(12, 0), 200, 21, 2));
        assertThat(viaGroup).isEqualTo(new CollectionReminderSettings(true, 6, 3, 4, LocalTime.of(9, 0), LocalTime.of(11, 0), 200, 7, 2));
    }

    @Test
    @Tag("D-369")
    void aRemovedExceptionDoesNotContributeItsSettings() {
        ReminderScheduleVersion removed = version(9, ReminderSchedule.followingGroup());
        ReminderScheduleVersion group = version(3, withNotices(5, new ReminderSendingOverrides(null, null, null, null, null, null, 200, null, null)));

        assertThat(ReminderScheduleResolver.resolve(Optional.of(removed), Optional.of(group), GLOBAL, null, Optional.empty()).settings().dailyEmailCap()).isEqualTo(200);
    }

    @Test
    @Tag("D-368")
    void resolvedNoticesComeInTimelineOrderEvenIfStoredShuffled() {
        List<ReminderNotice> shuffled = List.of(ReminderSchedule.initial().notices().get(4), ReminderSchedule.initial().notices().get(2),
                ReminderSchedule.initial().notices().get(1), ReminderSchedule.initial().notices().get(0), ReminderSchedule.initial().notices().get(3));

        ResolvedReminderSchedule resolved = ReminderScheduleResolver.resolve(Optional.empty(), Optional.of(version(3, new ReminderSchedule(false, shuffled, null))), GLOBAL, null,
                Optional.empty());

        assertThat(resolved.notices()).extracting(ReminderNotice::noticeKey).containsExactly("issuance", "after-issue", "before-due", "after-due", "before-suspension");
    }

    @Test
    @Tag("D-371")
    void theTemplateDoesNotExceedTheStudentCapsInAStandardMonth() {
        ResolvedReminderSchedule resolved = ReminderScheduleResolver.resolve(Optional.empty(), Optional.empty(), GLOBAL, null, Optional.of(MARCH));

        assertThat(resolved.trimmedNotices()).isEmpty();
    }

    @Test
    @Tag("D-371")
    void repetitionsBeyondTheMonthlyMaximumAreExposedAsTrimmed() {
        ReminderNotice weekly = new ReminderNotice("weekly", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1,
                EnumSet.allOf(ReminderChannel.class), new ReminderRepetition(3, ReminderMilestone.SUSPENSION));
        ReminderMilestoneDates dates = new ReminderMilestoneDates(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 3), LocalDate.of(2027, 3, 31));
        CollectionReminderSettings settings = new ReminderSendingOverrides(null, null, 3, 2, null, null, null, null, null).overlayOn(GLOBAL);

        List<TrimmedReminder> trimmed = ReminderEmailCapTrimmer.trimmedBy(List.of(weekly), settings, dates);

        assertThat(trimmed).extracting(TrimmedReminder::date).startsWith(LocalDate.of(2027, 3, 10));
        assertThat(trimmed).allSatisfy(reminder -> {
            assertThat(reminder.noticeKey()).isEqualTo("weekly");
            assertThat(reminder.reason()).isEqualTo(TrimmedReminder.TrimReason.MONTHLY_CAP);
        });
        assertThat(trimmed).extracting(TrimmedReminder::date).doesNotContain(LocalDate.of(2027, 3, 4), LocalDate.of(2027, 3, 7));
    }

    @Test
    @Tag("D-371")
    void aMailTooCloseToThePreviousOneIsDeferredAndTrimmedOnlyWhenItWouldNoLongerApply() {
        ReminderNotice first = new ReminderNotice("first", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -2, EnumSet.of(ReminderChannel.EMAIL), null);
        ReminderNotice second = new ReminderNotice("second", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -1, EnumSet.of(ReminderChannel.EMAIL), null);
        ReminderNotice afterDue = new ReminderNotice("afterDue", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1, EnumSet.of(ReminderChannel.EMAIL), null);
        ReminderMilestoneDates dates = new ReminderMilestoneDates(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 31));

        List<TrimmedReminder> trimmed = ReminderEmailCapTrimmer.trimmedBy(List.of(first, second, afterDue), GLOBAL, dates);

        assertThat(trimmed).containsExactly(new TrimmedReminder("second", LocalDate.of(2027, 3, 10), TrimmedReminder.TrimReason.MIN_SPACING));
    }

    @Test
    @Tag("D-371")
    @Tag("D-372")
    void aRepetitionThatCrossesTheDueDateIsJudgedDateByDate() {
        ReminderNotice crossing = new ReminderNotice("crossing", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, 5,
                EnumSet.allOf(ReminderChannel.class), new ReminderRepetition(5, ReminderMilestone.SUSPENSION));
        CollectionReminderSettings fiveAMonth = new ReminderSendingOverrides(null, null, 3, 5, null, null, null, null, null).overlayOn(GLOBAL);

        assertThat(ReminderEmailCapTrimmer.trimmedBy(List.of(crossing), fiveAMonth, MARCH)).isEmpty();
        assertThat(ReminderEmailCapTrimmer.trimmedBy(List.of(crossing), GLOBAL, MARCH))
                .containsExactly(new TrimmedReminder("crossing", LocalDate.of(2027, 3, 26), TrimmedReminder.TrimReason.MONTHLY_CAP));
        assertThat(ReminderNotice.isBeforeDueDate(LocalDate.of(2027, 3, 6), MARCH)).isTrue();
        assertThat(ReminderNotice.isBeforeDueDate(LocalDate.of(2027, 3, 11), MARCH)).isFalse();
        assertThat(ReminderNotice.isBeforeDueDate(LocalDate.of(2027, 3, 16), MARCH)).isFalse();
    }

    @Test
    @Tag("D-371")
    void aDateOutsideTheSendingWindowIsReportedWithItsOwnReason() {
        ReminderNotice late = new ReminderNotice("late", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 25,
                EnumSet.of(ReminderChannel.EMAIL), null);

        assertThat(ReminderEmailCapTrimmer.trimmedBy(List.of(late), GLOBAL, MARCH))
                .containsExactly(new TrimmedReminder("late", LocalDate.of(2027, 4, 5), TrimmedReminder.TrimReason.OUT_OF_WINDOW));
    }

    @Test
    @Tag("D-369")
    void theResolutionExposesTheVersionsThatContributed() {
        ReminderScheduleVersion group = version(3, withNotices(5, ReminderSendingOverrides.none()));
        ReminderScheduleVersion exception = version(7, withNotices(4, ReminderSendingOverrides.none()));
        ReminderScheduleVersion removed = version(9, ReminderSchedule.followingGroup());

        ResolvedReminderSchedule viaException = ReminderScheduleResolver.resolve(Optional.of(exception), Optional.of(group), GLOBAL, 11L, Optional.empty());
        ResolvedReminderSchedule viaRemoved = ReminderScheduleResolver.resolve(Optional.of(removed), Optional.of(group), GLOBAL, 11L, Optional.empty());
        ResolvedReminderSchedule template = ReminderScheduleResolver.resolve(Optional.empty(), Optional.empty(), GLOBAL, null, Optional.empty());

        assertThat(viaException).extracting("exceptionVersionId", "groupVersionId", "globalSettingsVersionId", "configurationVersionId").containsExactly(7L, 3L, 11L, 7L);
        assertThat(viaRemoved).extracting("exceptionVersionId", "groupVersionId", "globalSettingsVersionId", "configurationVersionId").containsExactly(null, 3L, 11L, 3L);
        assertThat(template).extracting("exceptionVersionId", "groupVersionId", "globalSettingsVersionId", "configurationVersionId").containsExactly(null, null, null, null);
    }

    @Test
    @Tag("D-371")
    void suspensionNoticesAndAppOnlyNoticesNeverCountForTheCaps() {
        ReminderNotice suspension = new ReminderNotice("s", ReminderMessageType.BEFORE_SUSPENSION_REMINDER, ReminderMilestone.SUSPENSION, -1, EnumSet.allOf(ReminderChannel.class), null);
        ReminderNotice appOnly = new ReminderNotice("app", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1, EnumSet.of(ReminderChannel.IN_APP),
                new ReminderRepetition(1, ReminderMilestone.SUSPENSION));
        CollectionReminderSettings tight = new ReminderSendingOverrides(null, null, 30, 1, null, null, null, null, null).overlayOn(GLOBAL);

        assertThat(ReminderEmailCapTrimmer.trimmedBy(List.of(suspension, appOnly), tight, MARCH)).isEmpty();
    }

    @Test
    @Tag("D-372")
    void beforeDueIsDecidedByTheMilestoneAndNotByTheMessageType() {
        ReminderNotice dueMinusTwo = new ReminderNotice("a", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, -2, EnumSet.of(ReminderChannel.EMAIL), null);
        ReminderNotice dueZero = new ReminderNotice("b", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, 0, EnumSet.of(ReminderChannel.EMAIL), null);
        ReminderNotice issuePlusFifteen = new ReminderNotice("c", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.ISSUANCE, 15, EnumSet.of(ReminderChannel.EMAIL), null);
        ReminderNotice issuePlusFive = new ReminderNotice("d", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, 5, EnumSet.of(ReminderChannel.EMAIL), null);
        ReminderNotice suspensionMinusOne = new ReminderNotice("e", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.SUSPENSION, -1, EnumSet.of(ReminderChannel.EMAIL), null);
        ReminderMilestoneDates tenDays = new ReminderMilestoneDates(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 31));

        assertThat(dueMinusTwo.isBeforeDueDate(tenDays)).isTrue();
        assertThat(dueZero.isBeforeDueDate(tenDays)).isFalse();
        assertThat(issuePlusFifteen.isBeforeDueDate(tenDays)).isFalse();
        assertThat(issuePlusFive.isBeforeDueDate(tenDays)).isTrue();
        assertThat(suspensionMinusOne.isBeforeDueDate(tenDays)).isFalse();
    }
}
