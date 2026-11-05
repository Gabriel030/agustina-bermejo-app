package com.red_escuela.backend.billing.configuration.dto;

import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedulePolicies;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTopicState;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;

import java.time.LocalDate;
import java.util.List;

public record ReminderSchedulePoliciesResponse(List<GroupPolicy> groups, List<ExceptionPolicy> exceptions) {

    public record GroupPolicy(TeachingGroup group, Long versionId, LocalDate effectiveFrom, int noticeCount, boolean hasOpenProposal) {
    }

    public record ExceptionPolicy(TeachingGroup group, Long targetId, Long versionId, LocalDate effectiveFrom, int noticeCount, boolean hasOpenProposal,
                                  LocalDate followsGroupFrom) {
    }

    public static ReminderSchedulePoliciesResponse from(ReminderSchedulePolicies policies) {
        return new ReminderSchedulePoliciesResponse(policies.groups().stream().map(ReminderSchedulePoliciesResponse::groupOf).toList(),
                policies.exceptions().stream().map(ReminderSchedulePoliciesResponse::exceptionOf).toList());
    }

    private static GroupPolicy groupOf(ReminderScheduleTopicState state) {
        int noticeCount = state.hasEffectiveVersion() ? state.schedule().notices().size() : ReminderSchedule.initial().notices().size();
        return new GroupPolicy(state.group(), state.versionId(), state.effectiveFrom(), noticeCount, state.hasOpenProposal());
    }

    private static ExceptionPolicy exceptionOf(ReminderScheduleTopicState state) {
        return new ExceptionPolicy(state.group(), state.targetId(), state.versionId(), state.effectiveFrom(), state.schedule().notices().size(),
                state.hasOpenProposal(), state.followsGroupFrom());
    }
}
