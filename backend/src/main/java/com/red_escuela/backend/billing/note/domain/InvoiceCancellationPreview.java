package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.billing.discount.domain.EnrollmentFeeDiscountFacts;
import com.red_escuela.backend.billing.discount.domain.ReleasedDiscountConsumption;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;
import java.util.Optional;

public record InvoiceCancellationPreview(List<PolicyViolation> blockedBy, NonNegativeMoney creditNoteAmount, boolean releasesChargeKey, boolean executesImmediately,
                                         List<LiveLateFeeToAnnul> lateFeesToAnnul, List<ReleasedDiscountConsumption> discountConsumptionReleased,
                                         Optional<EnrollmentFeeDiscountFacts> enrollmentFee) {

    public InvoiceCancellationPreview {
        blockedBy = List.copyOf(blockedBy);
        lateFeesToAnnul = List.copyOf(lateFeesToAnnul);
        discountConsumptionReleased = List.copyOf(discountConsumptionReleased);
    }

    public boolean allowed() {
        return blockedBy.isEmpty();
    }
}
