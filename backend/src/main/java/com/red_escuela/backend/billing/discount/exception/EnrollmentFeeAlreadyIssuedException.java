package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class EnrollmentFeeAlreadyIssuedException extends ConflictException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public EnrollmentFeeAlreadyIssuedException(List<PolicyViolation> violations) {
        super("La matrícula de ese año lectivo ya está emitida.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
