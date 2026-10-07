package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConfigurationChangeValidator {
    private final ChangeBillingConfigurationOperation operation;

    public ConfigurationChangeValidator(ChangeBillingConfigurationOperation operation) {
        this.operation = operation;
    }

    public List<PolicyViolation> collectViolations(ChangeBillingConfigurationCommand command) {
        try {
            operation.requireValidContext(command);
            return List.of();
        } catch (RuntimeException rejection) {
            if (rejection instanceof ReportsPolicyViolations reporting) {
                return reporting.violations();
            }
            throw rejection;
        }
    }
}
