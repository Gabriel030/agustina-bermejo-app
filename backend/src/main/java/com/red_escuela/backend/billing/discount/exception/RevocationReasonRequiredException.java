package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class RevocationReasonRequiredException extends BadRequestException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public RevocationReasonRequiredException(List<PolicyViolation> violations) {
        super("Revocar un descuento necesita un motivo de hasta 450 caracteres.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
