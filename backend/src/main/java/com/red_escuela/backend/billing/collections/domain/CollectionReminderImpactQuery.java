package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleProposal;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record CollectionReminderImpactQuery(ReminderScheduleProposal scheduleProposal, CollectionReminderSettings globalSettings, LocalDate effectiveFrom) {

    public CollectionReminderImpactQuery {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (scheduleProposal == null && globalSettings == null) {
            throw new IllegalArgumentException("The impact query needs a schedule proposal or global settings");
        }
    }

    public CollectionReminderImpactQuery(ReminderScheduleProposal proposal, LocalDate effectiveFrom) {
        this(Objects.requireNonNull(proposal, "proposal"), proposal.globalSettings(), effectiveFrom);
    }

    public static CollectionReminderImpactQuery ofGlobalSettings(CollectionReminderSettings globalSettings, LocalDate effectiveFrom) {
        return new CollectionReminderImpactQuery(null, Objects.requireNonNull(globalSettings, "globalSettings"), effectiveFrom);
    }

    public Optional<ReminderScheduleProposal> proposedSchedule() {
        return Optional.ofNullable(scheduleProposal);
    }

    public Optional<CollectionReminderSettings> proposedGlobalSettings() {
        return Optional.ofNullable(globalSettings);
    }
}
