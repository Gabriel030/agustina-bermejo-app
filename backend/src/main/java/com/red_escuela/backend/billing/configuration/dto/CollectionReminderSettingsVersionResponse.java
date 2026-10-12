package com.red_escuela.backend.billing.configuration.dto;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;

import java.time.LocalDate;

public record CollectionReminderSettingsVersionResponse(Long id, ConfigurationVersionStatus status, LocalDate effectiveFrom, String changeReason,
                                                        boolean directPublication, Long proposedById, Long decidedById, String decisionReason,
                                                        CollectionReminderSettingsValues values) {

    public static CollectionReminderSettingsVersionResponse from(ConfigurationVersion version) {
        return new CollectionReminderSettingsVersionResponse(version.getId(), version.getStatus(), version.getEffectiveFrom(), version.getChangeReason(),
                version.isDirectPublication(), version.getProposedById(), version.getDecidedById(), version.getDecisionReason(),
                CollectionReminderSettingsValues.from((CollectionReminderSettings) version.values()));
    }
}
