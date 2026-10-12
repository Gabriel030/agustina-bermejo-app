package com.red_escuela.backend.billing.note.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class PendingBankTransferOnInvoiceException extends ConflictException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public PendingBankTransferOnInvoiceException(List<PolicyViolation> violations) {
        super("Invoice has a pending bank transfer: " + violations.stream().map(PolicyViolation::code).toList());
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
