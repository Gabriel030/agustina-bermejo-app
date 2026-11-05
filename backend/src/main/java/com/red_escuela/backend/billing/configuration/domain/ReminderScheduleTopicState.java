package com.red_escuela.backend.billing.configuration.domain;

import java.time.LocalDate;

public record ReminderScheduleTopicState(ConfigurationTopic topic, Long versionId, LocalDate effectiveFrom, ReminderSchedule schedule, boolean hasOpenProposal,
                                         LocalDate followsGroupFrom) {

    public boolean hasEffectiveVersion() {
        return versionId != null;
    }

    public boolean isActiveException() {
        return topic.isReminderScheduleException() && hasEffectiveVersion() && !schedule.followsGroupSchedule();
    }

    public TeachingGroup group() {
        return topic.scope() == ConfigurationScope.PROGRAM_REMINDER_SCHEDULE ? TeachingGroup.PROGRAMS : TeachingGroup.MONTHLY_COURSES;
    }

    public Long targetId() {
        return topic.programId() != null ? topic.programId() : topic.courseId();
    }
}
