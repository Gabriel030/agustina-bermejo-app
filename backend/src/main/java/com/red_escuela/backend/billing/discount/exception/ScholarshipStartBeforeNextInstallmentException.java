package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class ScholarshipStartBeforeNextInstallmentException extends ConflictException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public ScholarshipStartBeforeNextInstallmentException(List<PolicyViolation> violations) {
        super("La beca no puede empezar antes de la próxima cuota a emitir.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
