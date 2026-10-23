package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleProposal;

import java.time.LocalDate;
import java.util.Objects;

public record CollectionReminderImpactQuery(ReminderScheduleProposal proposal, LocalDate effectiveFrom) {

    public CollectionReminderImpactQuery {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    }
}
