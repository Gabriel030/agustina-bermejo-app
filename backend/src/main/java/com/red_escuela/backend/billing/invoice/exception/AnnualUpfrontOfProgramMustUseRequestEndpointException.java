package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class AnnualUpfrontOfProgramMustUseRequestEndpointException extends ConflictException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public AnnualUpfrontOfProgramMustUseRequestEndpointException(List<PolicyViolation> violations) {
        super("El pago anual de una carrera se pide desde el pedido del pago anual.");
        this.violations = List.copyOf(violations);
    }

    public List<PolicyViolation> violations() {
        return violations;
    }
}
