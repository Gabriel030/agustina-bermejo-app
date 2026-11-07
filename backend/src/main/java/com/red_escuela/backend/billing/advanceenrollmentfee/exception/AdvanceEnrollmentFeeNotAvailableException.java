package com.red_escuela.backend.billing.advanceenrollmentfee.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class AdvanceEnrollmentFeeNotAvailableException extends ConflictException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public AdvanceEnrollmentFeeNotAvailableException(String reasonCode, String reason) {
        super("Advance enrollment fee not available: " + reasonCode);
        this.violations = List.of(new PolicyViolation(reasonCode, reason));
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
