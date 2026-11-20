package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class FinalExamBlockedByAbsencesException extends ConflictException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public FinalExamBlockedByAbsencesException(List<PolicyViolation> violations) {
        super("Un alumno libre por inasistencias no puede rendir el final hasta justificarlas.");
        this.violations = List.copyOf(violations);
    }

    public List<PolicyViolation> violations() {
        return violations;
    }
}
