package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class CohortPromotionAlreadyAcquiredException extends ConflictException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public CohortPromotionAlreadyAcquiredException(List<PolicyViolation> violations) {
        super("La inscripción ya tiene una promoción de cohorte.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
