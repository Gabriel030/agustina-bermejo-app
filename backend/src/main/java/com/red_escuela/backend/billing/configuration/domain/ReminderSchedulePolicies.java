package com.red_escuela.backend.billing.configuration.domain;

import java.util.List;

public record ReminderSchedulePolicies(List<ReminderScheduleTopicState> groups, List<ReminderScheduleTopicState> exceptions) {

    public ReminderSchedulePolicies {
        groups = List.copyOf(groups);
        exceptions = List.copyOf(exceptions);
    }

    public static ReminderSchedulePolicies of(List<ReminderScheduleTopicState> states) {
        return new ReminderSchedulePolicies(
                states.stream().filter(state -> !state.topic().isReminderScheduleException()).toList(),
                states.stream().filter(ReminderScheduleTopicState::isActiveException).toList());
    }
}
