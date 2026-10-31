package com.red_escuela.backend.billing.annualupfront.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class AnnualUpfrontPlanCannotBeChangedException extends ConflictException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public AnnualUpfrontPlanCannotBeChangedException(List<PolicyViolation> violations) {
        super("Con el pago anual confirmado el plan no se cambia durante el año lectivo.");
        this.violations = List.copyOf(violations);
    }

    public List<PolicyViolation> violations() {
        return violations;
    }
}
