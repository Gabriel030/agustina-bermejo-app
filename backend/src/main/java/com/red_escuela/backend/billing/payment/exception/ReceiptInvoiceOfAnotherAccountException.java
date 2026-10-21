package com.red_escuela.backend.billing.payment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class ReceiptInvoiceOfAnotherAccountException extends NotFoundException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public ReceiptInvoiceOfAnotherAccountException(List<PolicyViolation> violations) {
        super("The selected invoice does not belong to the account of the student");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
