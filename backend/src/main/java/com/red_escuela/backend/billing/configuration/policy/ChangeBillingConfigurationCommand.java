package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.operation.DirectorApprovable;

import java.time.LocalDate;

public record ChangeBillingConfigurationCommand(ConfigurationTopic topic, ConfigurationValues values, LocalDate effectiveFrom, String changeReason,
                                                Long requestedByPersonId, boolean requestedByDirector) implements DirectorApprovable {

    @Override
    public String authorizationReason() {
        return "Cambio de configuración de facturación (" + topic.key() + ") con vigencia desde " + effectiveFrom + ": " + changeReason;
    }

    @Override
    public LocalDate authorizationExpiresOn() {
        return effectiveFrom;
    }
}
