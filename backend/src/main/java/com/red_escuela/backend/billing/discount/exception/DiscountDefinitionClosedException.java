package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class DiscountDefinitionClosedException extends ConflictException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public DiscountDefinitionClosedException(List<PolicyViolation> violations) {
        super("La promoción está cerrada.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
