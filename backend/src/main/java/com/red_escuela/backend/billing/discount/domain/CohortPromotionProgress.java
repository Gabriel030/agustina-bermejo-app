package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;

import java.time.LocalDate;
import java.util.Objects;
import java.util.OptionalInt;

public record CohortPromotionProgress(StudentDiscount promotion, LocalDate availableUntil, long liveUses) {

    public CohortPromotionProgress {
        Objects.requireNonNull(promotion, "promotion");
    }

    public boolean canBeAppliedOn(LocalDate issueDate) {
        boolean withinWindow = availableUntil == null || !issueDate.isAfter(availableUntil);
        return withinWindow && liveUses < maximumUses();
    }

    public OptionalInt nextInstallmentNumber() {
        return isMonthlyInstallment() ? OptionalInt.of(Math.toIntExact(liveUses) + 1) : OptionalInt.empty();
    }

    private long maximumUses() {
        if (promotion.getInstallmentsTotal() != null) {
            return promotion.getInstallmentsTotal();
        }
        return isMonthlyInstallment() ? Long.MAX_VALUE : 1L;
    }

    private boolean isMonthlyInstallment() {
        return promotion.getApplicableInvoiceType() == InvoiceType.MONTHLY_INSTALLMENT;
    }
}
