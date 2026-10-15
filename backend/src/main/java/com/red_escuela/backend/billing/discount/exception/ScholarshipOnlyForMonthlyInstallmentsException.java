package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class ScholarshipOnlyForMonthlyInstallmentsException extends BadRequestException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public ScholarshipOnlyForMonthlyInstallmentsException(List<PolicyViolation> violations) {
        super("La beca solo aplica a cuotas mensuales.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
