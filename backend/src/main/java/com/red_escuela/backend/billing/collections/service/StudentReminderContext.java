package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.ReminderStudentFacts;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;

import java.util.Map;
import java.util.Objects;

public record StudentReminderContext(ReminderStudentFacts facts, Long personId, Map<Long, ReminderScheduleTarget> scheduleTargetsByInvoiceId) {

    public StudentReminderContext {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(personId, "personId");
        scheduleTargetsByInvoiceId = Map.copyOf(scheduleTargetsByInvoiceId);
    }

    public Long studentId() {
        return facts.studentId();
    }
}
