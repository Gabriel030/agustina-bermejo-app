package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public abstract class PaymentReviewRejectedException extends BadRequestException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    protected PaymentReviewRejectedException(String message, List<PolicyViolation> violations) {
        super(message);
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
