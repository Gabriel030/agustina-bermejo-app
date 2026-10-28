package com.red_escuela.backend.billing.discount.dto;

import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantPreview;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.lang.Nullable;

import java.time.YearMonth;
import java.util.List;

public record StudentDiscountGrantPreviewResponse(boolean allowed, List<PolicyViolation> violations, DiscountType type, InvoiceType invoiceType, MonetaryAdjustment calculation,
                                                  @Nullable YearMonth fromPeriod, @Nullable YearMonth toPeriod, YearMonth nextInstallmentPeriod, int coveredPeriods, int priority,
                                                  @Nullable Long discountDefinitionId, boolean annualUpfrontCoversRange, boolean enrollmentFeeAlreadyIssued,
                                                  @Nullable Long overlappingDiscountId, boolean requiresDirectorApproval) {

    public static StudentDiscountGrantPreviewResponse from(StudentDiscountGrantPreview preview) {
        return new StudentDiscountGrantPreviewResponse(preview.allowed(), preview.violations(), preview.type(), preview.invoiceType(), preview.calculation(), preview.fromPeriod(),
                preview.toPeriod(), preview.nextInstallmentPeriod(), preview.coveredPeriods(), preview.priority(), preview.discountDefinitionId(), preview.annualUpfrontCoversRange(),
                preview.enrollmentFeeAlreadyIssued(), preview.overlappingDiscountId(), preview.requiresDirectorApproval());
    }
}
