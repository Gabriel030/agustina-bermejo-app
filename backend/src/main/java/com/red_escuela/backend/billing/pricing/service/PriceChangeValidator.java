package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.pricing.policy.ChangePriceCommand;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceChangeValidator {
    private final ChangePriceOperation operation;

    public PriceChangeValidator(ChangePriceOperation operation) {
        this.operation = operation;
    }

    public List<PolicyViolation> collectViolations(ChangePriceCommand command) {
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
