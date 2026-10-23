package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDiscountDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.Instant;
import java.util.Objects;

public record InvoiceDiscountCandidate(
        DiscountType discountType,
        Long sourceId,
        Long studentDiscountId,
        String discountName,
        InvoiceType applicableInvoiceType,
        NonNegativeMoney baseAmount,
        NonNegativeMoney discountAmount,
        Integer installmentNumber,
        Integer installmentsTotal,
        int lineNumber,
        Integer priority,
        Instant grantedAt,
        MonetaryAdjustment calculation,
        DiscountCandidateOrigin origin) {

    private static final int FIRST_LINE_NUMBER = 1;

    public InvoiceDiscountCandidate {
        Objects.requireNonNull(discountType, "discountType");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(discountName, "discountName");
        Objects.requireNonNull(applicableInvoiceType, "applicableInvoiceType");
        Objects.requireNonNull(baseAmount, "baseAmount");
        Objects.requireNonNull(discountAmount, "discountAmount");
        Objects.requireNonNull(origin, "origin");
        if (lineNumber < FIRST_LINE_NUMBER) {
            throw new IllegalArgumentException("lineNumber must be at least " + FIRST_LINE_NUMBER);
        }
    }

    public static InvoiceDiscountCandidate ofLegacySource(DiscountCandidateOrigin origin, DiscountType discountType, Long sourceId, Long studentDiscountId,
                                                          String discountName, InvoiceType applicableInvoiceType, NonNegativeMoney baseAmount,
                                                          NonNegativeMoney discountAmount, Integer installmentNumber, Integer installmentsTotal) {
        return new InvoiceDiscountCandidate(discountType, sourceId, studentDiscountId, discountName, applicableInvoiceType, baseAmount, discountAmount,
                installmentNumber, installmentsTotal, FIRST_LINE_NUMBER, null, null, null, origin);
    }

    public InvoiceDiscountCandidate withStudentDiscountId(Long registeredStudentDiscountId) {
        return new InvoiceDiscountCandidate(discountType, sourceId, registeredStudentDiscountId, discountName, applicableInvoiceType, baseAmount, discountAmount,
                installmentNumber, installmentsTotal, lineNumber, priority, grantedAt, calculation, origin);
    }

    public int typeRank() {
        return switch (discountType) {
            case SCHOLARSHIP -> 1;
            case PROMOTION, COHORT_PROMOTION, INDIVIDUAL, COHORT_ENTRY -> 2;
            case ANNUAL_UPFRONT -> 3;
        };
    }

    public InvoiceDiscountDraft toDiscountDraft() {
        return new InvoiceDiscountDraft(discountType, discountName, studentDiscountId, baseAmount, discountAmount, installmentNumber, installmentsTotal, calculation);
    }
}
