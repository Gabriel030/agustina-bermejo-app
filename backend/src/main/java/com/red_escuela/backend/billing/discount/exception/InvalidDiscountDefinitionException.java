package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class InvalidDiscountDefinitionException extends BadRequestException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public InvalidDiscountDefinitionException(List<PolicyViolation> violations) {
        super("La promoción no es válida.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
