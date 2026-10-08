package com.red_escuela.backend.billing.configuration.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class ChangeReasonRequiredException extends BadRequestException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public ChangeReasonRequiredException(List<PolicyViolation> violations) {
        super("El cambio de configuración necesita un motivo.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
