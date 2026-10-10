package com.red_escuela.backend.billing.note.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class CreditNoteJustificationRequiredException extends BadRequestException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public CreditNoteJustificationRequiredException(List<PolicyViolation> violations) {
        super("Credit note justification required: " + violations.stream().map(PolicyViolation::code).toList());
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
