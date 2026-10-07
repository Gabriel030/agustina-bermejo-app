package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class FinalExamRequiresRegularStudentException extends BadRequestException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public FinalExamRequiresRegularStudentException(List<PolicyViolation> violations) {
        super("Solo un alumno regular en la materia puede rendir el final.");
        this.violations = List.copyOf(violations);
    }

    public List<PolicyViolation> violations() {
        return violations;
    }
}
