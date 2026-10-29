package com.red_escuela.backend.billing.annualupfront.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class AnnualUpfrontIdempotencyKeyInvalidException extends BadRequestException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public AnnualUpfrontIdempotencyKeyInvalidException(List<PolicyViolation> violations) {
        super("La clave de idempotencia es obligatoria");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
