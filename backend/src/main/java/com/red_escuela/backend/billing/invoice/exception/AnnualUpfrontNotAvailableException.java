package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class AnnualUpfrontNotAvailableException extends ConflictException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public AnnualUpfrontNotAvailableException(List<PolicyViolation> violations) {
        super("El pago anual de carrera no está disponible.");
        this.violations = List.copyOf(violations);
    }

    public List<PolicyViolation> violations() {
        return violations;
    }
}
