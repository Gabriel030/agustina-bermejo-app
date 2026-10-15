package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.discount.exception.CurrencyMismatchException;
import com.red_escuela.backend.billing.discount.exception.DiscountClosureReasonRequiredException;
import com.red_escuela.backend.billing.discount.exception.DiscountTypeNotInCatalogException;
import com.red_escuela.backend.billing.discount.exception.InstallmentCountOnlyForMonthlyInstallmentsException;
import com.red_escuela.backend.billing.discount.exception.InvalidDiscountDefinitionException;
import com.red_escuela.backend.billing.discount.exception.ScholarshipOnlyForMonthlyInstallmentsException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public final class DiscountDefinitionRejections {

    private DiscountDefinitionRejections() {
    }

    public static RuntimeException rejectionOf(List<PolicyViolation> violations) {
        return switch (violations.get(0).code()) {
            case "INSTALLMENT_COUNT_ONLY_FOR_MONTHLY_INSTALLMENTS" -> new InstallmentCountOnlyForMonthlyInstallmentsException(violations);
            case "SCHOLARSHIP_ONLY_FOR_MONTHLY_INSTALLMENTS" -> new ScholarshipOnlyForMonthlyInstallmentsException(violations);
            case "DISCOUNT_TYPE_NOT_IN_CATALOG" -> new DiscountTypeNotInCatalogException(violations);
            case "CURRENCY_MISMATCH" -> new CurrencyMismatchException(violations);
            case "DISCOUNT_CLOSURE_REASON_REQUIRED" -> new DiscountClosureReasonRequiredException(violations);
            default -> new InvalidDiscountDefinitionException(violations);
        };
    }
}
