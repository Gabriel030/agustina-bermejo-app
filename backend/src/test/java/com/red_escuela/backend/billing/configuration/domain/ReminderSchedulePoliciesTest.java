package com.red_escuela.backend.billing.configuration.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderSchedulePoliciesTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 3);

    private static ReminderScheduleTopicState state(ConfigurationTopic topic, ReminderSchedule schedule) {
        return new ReminderScheduleTopicState(topic, schedule == null ? null : 7L, schedule == null ? null : TODAY, schedule, false, null);
    }

    @Test
    @Tag("D-368")
    void onlyEffectiveExceptionsThatKeepTheirOwnListAreSummarizedAndGroupsAreAlwaysKept() {
        ReminderScheduleTopicState group = state(ConfigurationTopic.ofReminderScheduleGroup(TeachingGroup.PROGRAMS), null);
        ReminderScheduleTopicState active = state(ConfigurationTopic.ofReminderScheduleException(TeachingGroup.PROGRAMS, 5L), ReminderSchedule.initial());
        ReminderScheduleTopicState removed = state(ConfigurationTopic.ofReminderScheduleException(TeachingGroup.PROGRAMS, 6L), ReminderSchedule.followingGroup());
        ReminderScheduleTopicState notYetEffective = state(ConfigurationTopic.ofReminderScheduleException(TeachingGroup.PROGRAMS, 8L), null);

        ReminderSchedulePolicies policies = ReminderSchedulePolicies.of(List.of(group, active, removed, notYetEffective));

        assertThat(policies.groups()).containsExactly(group);
        assertThat(policies.exceptions()).containsExactly(active);
    }

    @Test
    @Tag("D-368")
    void aTopicStateKnowsItsGroupAndItsTarget() {
        ReminderScheduleTopicState program = state(ConfigurationTopic.ofReminderScheduleException(TeachingGroup.PROGRAMS, 5L), ReminderSchedule.initial());
        ReminderScheduleTopicState course = state(ConfigurationTopic.ofReminderScheduleException(TeachingGroup.MONTHLY_COURSES, 9L), ReminderSchedule.initial());
        ReminderScheduleTopicState courseGroup = state(ConfigurationTopic.ofReminderScheduleGroup(TeachingGroup.MONTHLY_COURSES), ReminderSchedule.initial());

        assertThat(program.group()).isEqualTo(TeachingGroup.PROGRAMS);
        assertThat(program.targetId()).isEqualTo(5L);
        assertThat(course.group()).isEqualTo(TeachingGroup.MONTHLY_COURSES);
        assertThat(course.targetId()).isEqualTo(9L);
        assertThat(courseGroup.targetId()).isNull();
        assertThat(courseGroup.hasEffectiveVersion()).isTrue();
        assertThat(state(ConfigurationTopic.ofReminderScheduleGroup(TeachingGroup.PROGRAMS), null).hasEffectiveVersion()).isFalse();
    }

    @Test
    @Tag("D-368")
    void normalizingAScheduleStoresItsNoticesByMilestoneAndDays() {
        ReminderNotice afterDue = ReminderNotice.of("after-due", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 10, EnumSet.of(ReminderChannel.EMAIL));
        ReminderNotice beforeSuspension = ReminderNotice.of("before-suspension", ReminderMessageType.BEFORE_SUSPENSION_REMINDER, ReminderMilestone.SUSPENSION, -1,
                EnumSet.of(ReminderChannel.EMAIL));
        ReminderNotice afterIssue = ReminderNotice.of("after-issue", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, 5, EnumSet.of(ReminderChannel.IN_APP));
        ReminderSchedule unordered = new ReminderSchedule(false, List.of(beforeSuspension, afterDue, afterIssue), null);

        ProgramReminderSchedule program = (ProgramReminderSchedule) new ProgramReminderSchedule(unordered).normalized();
        CourseReminderSchedule course = (CourseReminderSchedule) new CourseReminderSchedule(unordered).normalized();

        assertThat(program.schedule().notices()).containsExactly(afterIssue, afterDue, beforeSuspension);
        assertThat(course.schedule().notices()).containsExactly(afterIssue, afterDue, beforeSuspension);
        assertThat(unordered.notices()).containsExactly(beforeSuspension, afterDue, afterIssue);
        assertThat(CollectionReminderSettings.initial().normalized()).isEqualTo(CollectionReminderSettings.initial());
    }
}
