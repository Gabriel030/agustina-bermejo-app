package com.red_escuela.backend.billing.collections.dto;

import com.red_escuela.backend.billing.collections.domain.CollectionReminderImpact;

public record CollectionReminderImpactResponse(int studentsWithChangedSchedule) {

    public static CollectionReminderImpactResponse from(CollectionReminderImpact impact) {
        return new CollectionReminderImpactResponse(impact.studentsWithChangedSchedule());
    }
}
