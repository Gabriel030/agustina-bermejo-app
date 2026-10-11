package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class PaymentGatewayCapabilityNotSupportedException extends ConflictException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public PaymentGatewayCapabilityNotSupportedException(List<PolicyViolation> violations) {
        super("Payment gateway capability not supported: " + violations.stream().map(PolicyViolation::message).toList());
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
