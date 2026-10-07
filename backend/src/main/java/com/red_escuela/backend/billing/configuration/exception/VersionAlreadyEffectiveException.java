package com.red_escuela.backend.billing.configuration.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class VersionAlreadyEffectiveException extends ConflictException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public VersionAlreadyEffectiveException(List<PolicyViolation> violations) {
        super("Solo se puede cancelar una versión programada que todavía no rige.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
