package com.red_escuela.backend.billing.configuration.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class ProposalAlreadyOpenException extends ConflictException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public ProposalAlreadyOpenException(List<PolicyViolation> violations) {
        super("Ya hay una propuesta pendiente sobre este tema.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
