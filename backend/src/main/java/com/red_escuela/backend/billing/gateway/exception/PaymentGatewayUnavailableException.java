package com.red_escuela.backend.billing.gateway.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class PaymentGatewayUnavailableException extends ConflictException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public PaymentGatewayUnavailableException() {
        this(List.of());
    }

    public PaymentGatewayUnavailableException(List<PolicyViolation> violations) {
        super("The online payment gateway is not enabled");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
