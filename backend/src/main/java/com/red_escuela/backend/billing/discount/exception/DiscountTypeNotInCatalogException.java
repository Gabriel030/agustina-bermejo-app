package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;

import java.util.List;

public class DiscountTypeNotInCatalogException extends BadRequestException implements ReportsPolicyViolations {
    private final List<PolicyViolation> violations;

    public DiscountTypeNotInCatalogException(List<PolicyViolation> violations) {
        super("El tipo de descuento no se ofrece en el catálogo.");
        this.violations = List.copyOf(violations);
    }

    @Override
    public List<PolicyViolation> violations() {
        return violations;
    }
}
