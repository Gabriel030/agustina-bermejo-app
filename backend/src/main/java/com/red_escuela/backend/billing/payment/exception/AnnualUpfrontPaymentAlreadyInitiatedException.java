package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class AnnualUpfrontPaymentAlreadyInitiatedException extends ConflictException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public AnnualUpfrontPaymentAlreadyInitiatedException(List<PolicyViolation> violations) {
        super("The annual upfront request already has a payment under review");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
