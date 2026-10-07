package com.red_escuela.backend.billing.configuration.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class BillingConfigurationFrozenException extends ConflictException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public BillingConfigurationFrozenException(List<PolicyViolation> violations) {
        super("Este cambio afecta cargos ya emitidos y no se puede aplicar.");
        this.violations = List.copyOf(violations);
    }

    public List<PolicyViolation> violations() {
        return violations;
    }
}
