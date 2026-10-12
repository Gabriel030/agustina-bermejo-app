package com.red_escuela.backend.billing.pricing.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class InvalidMarketingDiscountException extends BadRequestException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public InvalidMarketingDiscountException(List<PolicyViolation> violations) {
        super("El descuento de marketing no es válido.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
