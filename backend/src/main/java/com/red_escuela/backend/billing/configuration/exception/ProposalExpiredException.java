package com.red_escuela.backend.billing.configuration.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class ProposalExpiredException extends ConflictException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public ProposalExpiredException(List<PolicyViolation> violations) {
        super("La propuesta venció sin decisión.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
