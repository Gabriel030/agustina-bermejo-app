package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.time.LocalDate;
import java.util.List;

public interface ValidateBillingConfigurationChangeUseCase {
    List<PolicyViolation> validateChange(ConfigurationTopic topic, ConfigurationValues values, LocalDate effectiveFrom, String changeReason);
}
