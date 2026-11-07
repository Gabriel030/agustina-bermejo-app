package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class AnnualUpfrontPaymentExpiredException extends ConflictException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public AnnualUpfrontPaymentExpiredException(List<PolicyViolation> violations) {
        super("The annual upfront request expired or was discarded");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
