package com.red_escuela.backend.billing.annualupfront.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class AnnualUpfrontTermsNotAcceptedException extends BadRequestException implements ReportsPolicyViolations {

    private final transient List<PolicyViolation> violations;

    public AnnualUpfrontTermsNotAcceptedException(List<PolicyViolation> violations) {
        super("Para pedir el pago anual hay que aceptar la condición vigente");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
