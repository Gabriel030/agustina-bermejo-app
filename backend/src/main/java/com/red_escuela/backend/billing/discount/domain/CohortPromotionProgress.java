package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

public record CohortPromotionProgress(StudentDiscount promotion, LocalDate availableUntil, List<AppliedStudentDiscountInstallment> liveApplications) {

    public CohortPromotionProgress {
        Objects.requireNonNull(promotion, "promotion");
        liveApplications = List.copyOf(liveApplications);
    }

    public long liveUses() {
        return liveApplications.size();
    }

    public boolean canBeAppliedOn(LocalDate issueDate) {
        boolean withinWindow = availableUntil == null || !issueDate.isAfter(availableUntil);
        return withinWindow && liveUses() < maximumUses();
    }

    public OptionalInt nextInstallmentNumber() {
        if (!isMonthlyInstallment()) {
            return OptionalInt.empty();
        }
        Set<Integer> used = liveApplications.stream().map(AppliedStudentDiscountInstallment::installmentNumber).filter(Objects::nonNull).collect(Collectors.toSet());
        int candidate = 1;
        while (used.contains(candidate)) {
            candidate++;
        }
        return OptionalInt.of(candidate);
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
