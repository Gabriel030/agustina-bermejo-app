package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.lang.Nullable;

import java.time.YearMonth;
import java.util.List;

public record StudentDiscountGrantPreview(List<PolicyViolation> violations, DiscountType type, InvoiceType invoiceType, MonetaryAdjustment calculation,
                                          @Nullable YearMonth fromPeriod, @Nullable YearMonth toPeriod, YearMonth nextInstallmentPeriod, int coveredPeriods, int priority,
                                          @Nullable Long discountDefinitionId, boolean annualUpfrontCoversRange, boolean enrollmentFeeAlreadyIssued,
                                          @Nullable Long overlappingDiscountId, boolean requiresDirectorApproval) {

    public StudentDiscountGrantPreview {
        violations = List.copyOf(violations);
    }

    public boolean allowed() {
        return violations.isEmpty();
    }
}
