package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.collections.domain.LateFeeSettlementSource;
import com.red_escuela.backend.billing.collections.domain.PlannedLateFeePeriod;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public record LateFeeIssuingContext(LateFeeOriginFacts origin, LateFeeDebitNoteDraftFacts draft, PlannedLateFeePeriod period, Long dueDateVersionId,
                                    LatePaymentTerms dueDateTerms, Predicate<LocalDate> frozenOn, Optional<LocalDate> enrollmentEndDate,
                                    NonNegativeMoney lateFeeAmount, LocalDate issueDate, Optional<LateFeeSettlementSource> settlementSource) {

    public LateFeeIssuingContext {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(dueDateVersionId, "dueDateVersionId");
        Objects.requireNonNull(dueDateTerms, "dueDateTerms");
        Objects.requireNonNull(frozenOn, "frozenOn");
        Objects.requireNonNull(enrollmentEndDate, "enrollmentEndDate");
        Objects.requireNonNull(lateFeeAmount, "lateFeeAmount");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(settlementSource, "settlementSource");
    }

    public LocalDate debitNoteDueDate() {
        return issueDate.plusDays(dueDateTerms.lateFeeDebitNoteDueDays());
    }
}
