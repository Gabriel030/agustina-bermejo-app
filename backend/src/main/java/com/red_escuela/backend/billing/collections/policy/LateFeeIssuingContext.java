package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.collections.domain.PlannedLateFeePeriod;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record LateFeeIssuingContext(LateFeeOriginFacts origin, LateFeeDebitNoteDraftFacts draft, PlannedLateFeePeriod period, Long dueDateVersionId,
                                    LatePaymentTerms dueDateTerms, LatePaymentTerms cutoffTerms, Optional<LocalDate> enrollmentEndDate,
                                    NonNegativeMoney lateFeeAmount, LocalDate issueDate, Optional<Long> settlementAllocationId) {

    public LateFeeIssuingContext {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(dueDateVersionId, "dueDateVersionId");
        Objects.requireNonNull(dueDateTerms, "dueDateTerms");
        Objects.requireNonNull(cutoffTerms, "cutoffTerms");
        Objects.requireNonNull(enrollmentEndDate, "enrollmentEndDate");
        Objects.requireNonNull(lateFeeAmount, "lateFeeAmount");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(settlementAllocationId, "settlementAllocationId");
    }

    public LocalDate debitNoteDueDate() {
        return issueDate.plusDays(dueDateTerms.lateFeeDebitNoteDueDays());
    }
}
