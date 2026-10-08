package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class InvoiceIssuingRejectedException extends ConflictException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public InvoiceIssuingRejectedException(List<PolicyViolation> violations) {
        super("Invoice issuing rejected: " + violations.stream().map(PolicyViolation::code).toList());
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
