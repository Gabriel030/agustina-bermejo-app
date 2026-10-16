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

        ResolvedReminderSchedule viaException = ReminderScheduleResolver.resolve(Optional.of(exception), Optional.of(group), GLOBAL, null);
        ResolvedReminderSchedule viaGroup = ReminderScheduleResolver.resolve(Optional.empty(), Optional.of(group), GLOBAL, null);

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
        ResolvedReminderSchedule resolved = ReminderScheduleResolver.resolve(Optional.empty(), Optional.empty(), GLOBAL, null);

        assertThat(resolved.source()).isEqualTo(ReminderScheduleSource.TEMPLATE);
        assertThat(resolved.configurationVersionId()).isNull();
        assertThat(resolved.notices()).hasSize(5);
        assertThat(resolved.settings()).isEqualTo(GLOBAL);
    }

    @Test
    @Tag("D-368")
    void aRemovedExceptionFallsBackToTheGroupAndToTheTemplateWhenThereIsNoGroup() {
        ReminderScheduleVersion removed = version(9, ReminderSchedule.followingGroup());
        ReminderScheduleVersion group = version(3, withNotices(5, ReminderSendingOverrides.none()));

        assertThat(ReminderScheduleResolver.resolve(Optional.of(removed), Optional.of(group), GLOBAL, null).source()).isEqualTo(ReminderScheduleSource.GROUP);
        assertThat(ReminderScheduleResolver.resolve(Optional.of(removed), Optional.empty(), GLOBAL, null).source()).isEqualTo(ReminderScheduleSource.TEMPLATE);
    }

    @Test
    @Tag("D-369")
    void settingsResolveFieldByFieldFromExceptionThenGroupThenGlobal() {
        ReminderSendingOverrides groupOverrides = new ReminderSendingOverrides(null, null, null, null, 200, 7, null);
        ReminderSendingOverrides exceptionOverrides = new ReminderSendingOverrides(false, null, LocalTime.of(10, 0), LocalTime.of(12, 0), null, 21, null);
        ReminderScheduleVersion group = version(3, withNotices(5, groupOverrides));
        ReminderScheduleVersion exception = version(7, withNotices(4, exceptionOverrides));

        CollectionReminderSettings viaException = ReminderScheduleResolver.resolve(Optional.of(exception), Optional.of(group), GLOBAL, null).settings();
        CollectionReminderSettings viaGroup = ReminderScheduleResolver.resolve(Optional.empty(), Optional.of(group), GLOBAL, null).settings();

        assertThat(viaException).isEqualTo(new CollectionReminderSettings(false, 6, LocalTime.of(10, 0), LocalTime.of(12, 0), 200, 21, 2));
        assertThat(viaGroup).isEqualTo(new CollectionReminderSettings(true, 6, LocalTime.of(9, 0), LocalTime.of(11, 0), 200, 7, 2));
    }

    @Test
    @Tag("D-369")
    void aRemovedExceptionDoesNotContributeItsSettings() {
        ReminderScheduleVersion removed = version(9, ReminderSchedule.followingGroup());
        ReminderScheduleVersion group = version(3, withNotices(5, new ReminderSendingOverrides(null, null, null, null, 200, null, null)));

        assertThat(ReminderScheduleResolver.resolve(Optional.of(removed), Optional.of(group), GLOBAL, null).settings().dailyEmailCap()).isEqualTo(200);
    }

    @Test
    @Tag("D-368")
    void resolvedNoticesComeInTimelineOrderEvenIfStoredShuffled() {
        List<ReminderNotice> shuffled = List.of(ReminderSchedule.initial().notices().get(4), ReminderSchedule.initial().notices().get(2),
                ReminderSchedule.initial().notices().get(1), ReminderSchedule.initial().notices().get(0), ReminderSchedule.initial().notices().get(3));

        ResolvedReminderSchedule resolved = ReminderScheduleResolver.resolve(Optional.empty(), Optional.of(version(3, new ReminderSchedule(false, shuffled, null))), GLOBAL, null);

        assertThat(resolved.notices()).extracting(ReminderNotice::noticeKey).containsExactly("issuance", "after-issue", "before-due", "after-due", "before-suspension");
    }

    @Test
    @Tag("D-391")
    void sixEmailNoticesInOneMonthAreAllResolvedWithoutAnyCap() {
        List<ReminderNotice> six = List.of(
                emailNotice("a", -6), emailNotice("b", -5), emailNotice("c", -4), emailNotice("d", -3), emailNotice("e", -2), emailNotice("f", -1));
        ReminderScheduleVersion group = version(3, new ReminderSchedule(false, six, ReminderSendingOverrides.none()));

        ResolvedReminderSchedule resolved = ReminderScheduleResolver.resolve(Optional.empty(), Optional.of(group), GLOBAL, null);

        assertThat(resolved.notices()).extracting(ReminderNotice::noticeKey).containsExactly("a", "b", "c", "d", "e", "f");
        assertThat(resolved.notices()).allSatisfy(notice -> assertThat(notice.sendsEmail()).isTrue());
    }

    @Test
    @Tag("D-391")
    void aWeeklyRepetitionIsKeptWholeInsteadOfBeingTrimmed() {
        ReminderNotice weekly = new ReminderNotice("weekly", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1,
                EnumSet.allOf(ReminderChannel.class), new ReminderRepetition(3, ReminderMilestone.SUSPENSION));
        ReminderScheduleVersion group = version(3, new ReminderSchedule(false, List.of(weekly), ReminderSendingOverrides.none()));

        ResolvedReminderSchedule resolved = ReminderScheduleResolver.resolve(Optional.empty(), Optional.of(group), GLOBAL, null);

        assertThat(resolved.notices()).containsExactly(weekly);
    }

    private static ReminderNotice emailNotice(String key, int days) {
        return new ReminderNotice(key, ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, days, EnumSet.of(ReminderChannel.EMAIL), null);
    }

    @Test
    @Tag("D-369")
    void theResolutionExposesTheVersionsThatContributed() {
        ReminderScheduleVersion group = version(3, withNotices(5, ReminderSendingOverrides.none()));
        ReminderScheduleVersion exception = version(7, withNotices(4, ReminderSendingOverrides.none()));
        ReminderScheduleVersion removed = version(9, ReminderSchedule.followingGroup());

        ResolvedReminderSchedule viaException = ReminderScheduleResolver.resolve(Optional.of(exception), Optional.of(group), GLOBAL, 11L);
        ResolvedReminderSchedule viaRemoved = ReminderScheduleResolver.resolve(Optional.of(removed), Optional.of(group), GLOBAL, 11L);
        ResolvedReminderSchedule template = ReminderScheduleResolver.resolve(Optional.empty(), Optional.empty(), GLOBAL, null);

        assertThat(viaException).extracting("exceptionVersionId", "groupVersionId", "globalSettingsVersionId", "configurationVersionId").containsExactly(7L, 3L, 11L, 7L);
        assertThat(viaRemoved).extracting("exceptionVersionId", "groupVersionId", "globalSettingsVersionId", "configurationVersionId").containsExactly(null, 3L, 11L, 3L);
        assertThat(template).extracting("exceptionVersionId", "groupVersionId", "globalSettingsVersionId", "configurationVersionId").containsExactly(null, null, null, null);
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
