package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class DiscountClosureReasonRequiredException extends BadRequestException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public DiscountClosureReasonRequiredException(List<PolicyViolation> violations) {
        super("Cerrar una promoción necesita un motivo.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
