package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDiscountDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

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
        Integer installmentsTotal) {

    public InvoiceDiscountCandidate {
        Objects.requireNonNull(discountType, "discountType");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(discountName, "discountName");
        Objects.requireNonNull(applicableInvoiceType, "applicableInvoiceType");
        Objects.requireNonNull(baseAmount, "baseAmount");
        Objects.requireNonNull(discountAmount, "discountAmount");
    }

    public boolean offersMoreThan(InvoiceDiscountCandidate rival) {
        int byAmount = discountAmount.value().amount().compareTo(rival.discountAmount.value().amount());
        if (byAmount != 0) {
            return byAmount > 0;
        }
        int bySourceId = sourceId.compareTo(rival.sourceId);
        if (bySourceId != 0) {
            return bySourceId < 0;
        }
        return discountType.compareTo(rival.discountType) < 0;
    }

    public InvoiceDiscountDraft toDiscountDraft() {
        return new InvoiceDiscountDraft(discountType, discountName, studentDiscountId, baseAmount, discountAmount, installmentNumber, installmentsTotal);
    }
}
