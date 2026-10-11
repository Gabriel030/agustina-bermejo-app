package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class IdempotencyKeyConflictException extends ConflictException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public IdempotencyKeyConflictException(List<PolicyViolation> violations) {
        super("Idempotency key reused with a different request");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
