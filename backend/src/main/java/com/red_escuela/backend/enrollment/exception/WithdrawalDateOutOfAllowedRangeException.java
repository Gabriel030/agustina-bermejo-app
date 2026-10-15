package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class WithdrawalDateOutOfAllowedRangeException extends BadRequestException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public WithdrawalDateOutOfAllowedRangeException(List<PolicyViolation> violations) {
        super("Withdrawal date out of allowed range: " + violations.stream().map(PolicyViolation::code).toList());
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
