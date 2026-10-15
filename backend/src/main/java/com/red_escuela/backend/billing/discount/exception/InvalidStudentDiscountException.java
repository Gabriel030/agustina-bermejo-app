package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class InvalidStudentDiscountException extends BadRequestException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public InvalidStudentDiscountException(List<PolicyViolation> violations) {
        super("El descuento no es válido.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
