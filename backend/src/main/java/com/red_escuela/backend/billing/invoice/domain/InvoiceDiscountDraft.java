package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.Objects;

public record InvoiceDiscountDraft(
        DiscountType discountType,
        String concept,
        Long studentDiscountId,
        NonNegativeMoney baseAmount,
        NonNegativeMoney discountAmount,
        Integer installmentNumber,
        Integer installmentsTotal,
        Long sourceInstallmentId) {

    public InvoiceDiscountDraft(DiscountType discountType, String concept, Long studentDiscountId, NonNegativeMoney baseAmount,
                                NonNegativeMoney discountAmount, Integer installmentNumber, Integer installmentsTotal) {
        this(discountType, concept, studentDiscountId, baseAmount, discountAmount, installmentNumber, installmentsTotal, null);
    }


    private static final int MAX_CONCEPT_LENGTH = 200;

    public InvoiceDiscountDraft {
        Objects.requireNonNull(discountType, "discountType");
        Objects.requireNonNull(baseAmount, "baseAmount");
        Objects.requireNonNull(discountAmount, "discountAmount");
        if (concept == null || concept.isBlank() || concept.length() > MAX_CONCEPT_LENGTH) {
            throw new IllegalArgumentException("concept must have between 1 and " + MAX_CONCEPT_LENGTH + " characters");
        }
        if (discountAmount.value().isZero()) {
            throw new IllegalArgumentException("A discount amount must be greater than zero");
        }
        boolean hasProgress = installmentNumber != null;
        if (installmentsTotal != null && !hasProgress) {
            throw new IllegalArgumentException("installmentsTotal requires installmentNumber");
        }
        if (hasProgress && (installmentNumber < 1 || (installmentsTotal != null && installmentNumber > installmentsTotal))) {
            throw new IllegalArgumentException("Invalid installment progress");
        }
    }
}
