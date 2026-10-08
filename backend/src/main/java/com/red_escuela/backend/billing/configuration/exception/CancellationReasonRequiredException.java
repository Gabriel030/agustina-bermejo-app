package com.red_escuela.backend.billing.configuration.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class CancellationReasonRequiredException extends BadRequestException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public CancellationReasonRequiredException(List<PolicyViolation> violations) {
        super("Cancelar una versión necesita un motivo.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
