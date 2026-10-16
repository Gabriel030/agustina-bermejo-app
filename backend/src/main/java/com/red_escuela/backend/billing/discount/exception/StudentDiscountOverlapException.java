package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class StudentDiscountOverlapException extends ConflictException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public StudentDiscountOverlapException(List<PolicyViolation> violations) {
        super("El descuento se superpone con otro vigente.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
