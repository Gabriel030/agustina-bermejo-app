package com.red_escuela.backend.billing.note.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class CreditNoteLinesInvalidException extends BadRequestException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public CreditNoteLinesInvalidException(List<PolicyViolation> violations) {
        super("Invalid credit note lines: " + violations.stream().map(PolicyViolation::code).toList());
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
