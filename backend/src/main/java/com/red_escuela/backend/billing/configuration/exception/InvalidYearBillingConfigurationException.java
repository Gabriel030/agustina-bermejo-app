package com.red_escuela.backend.billing.configuration.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class InvalidYearBillingConfigurationException extends BadRequestException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public InvalidYearBillingConfigurationException(List<PolicyViolation> violations) {
        super("La configuración tiene errores. Revisá los campos marcados.");
        this.violations = List.copyOf(violations);
    }

    public List<PolicyViolation> violations() {
        return violations;
    }
}
