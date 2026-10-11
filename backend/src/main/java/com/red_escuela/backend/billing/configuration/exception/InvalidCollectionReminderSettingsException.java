package com.red_escuela.backend.billing.configuration.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class InvalidCollectionReminderSettingsException extends BadRequestException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public InvalidCollectionReminderSettingsException(List<PolicyViolation> violations) {
        super("La configuración de avisos de cobranza tiene valores incoherentes.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
