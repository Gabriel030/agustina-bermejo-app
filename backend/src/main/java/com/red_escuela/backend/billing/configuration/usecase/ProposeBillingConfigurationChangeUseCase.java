package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;

import java.time.LocalDate;

public interface ProposeBillingConfigurationChangeUseCase {
    BillingOperationOutcome<ConfigurationVersion> proposeChange(ConfigurationTopic topic, ConfigurationValues values, LocalDate effectiveFrom, String changeReason);
}
