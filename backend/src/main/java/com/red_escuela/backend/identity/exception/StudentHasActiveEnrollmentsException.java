package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class StudentHasActiveEnrollmentsException extends ConflictException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public StudentHasActiveEnrollmentsException(List<PolicyViolation> violations) {
        super("Student has active enrollments: " + violations.stream().map(PolicyViolation::code).toList());
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
