package com.red_escuela.backend.billing.annualupfront.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class AnnualUpfrontTermsOutdatedException extends ConflictException implements ReportsPolicyViolations {
    private final transient List<PolicyViolation> violations;

    public AnnualUpfrontTermsOutdatedException(List<PolicyViolation> violations) {
        super("Los términos del pago anual cambiaron: hay que aceptar la versión vigente");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
