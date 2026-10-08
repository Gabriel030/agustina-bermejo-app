package com.red_escuela.backend.billing.configuration.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class RetroactiveEffectiveDateException extends BadRequestException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public RetroactiveEffectiveDateException(List<PolicyViolation> violations) {
        super("La vigencia del cambio no puede ser anterior a hoy.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
