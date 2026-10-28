package com.red_escuela.backend.billing.configuration.dto;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;

import java.time.LocalDate;
import java.util.Map;

public record CollectionReminderSettingsVersionResponse(Long id, ConfigurationVersionStatus status, LocalDate effectiveFrom, String changeReason,
                                                        boolean directPublication, Long proposedById, String proposedByName, Long decidedById, String decidedByName,
                                                        String decisionReason,
                                                        CollectionReminderSettingsValues values) {

    public static CollectionReminderSettingsVersionResponse from(ConfigurationVersion version, Map<Long, String> authorNames) {
        return new CollectionReminderSettingsVersionResponse(version.getId(), version.getStatus(), version.getEffectiveFrom(), version.getChangeReason(),
                version.isDirectPublication(), version.getProposedById(), nameOf(authorNames, version.getProposedById()),
                version.getDecidedById(), nameOf(authorNames, version.getDecidedById()), version.getDecisionReason(),
                CollectionReminderSettingsValues.from((CollectionReminderSettings) version.values()));
    }

    private static String nameOf(Map<Long, String> authorNames, Long personId) {
        return personId == null ? null : authorNames.get(personId);
    }
}
