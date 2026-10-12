package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class DuplicateInvoiceChargeException extends ConflictException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public DuplicateInvoiceChargeException(List<PolicyViolation> violations) {
        super("Duplicate charge detected: " + violations.stream().map(PolicyViolation::code).toList());
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
