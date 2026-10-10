package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.collections.domain.LateFeeMonth;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record LateFeeIssuingContext(LateFeeOriginFacts origin, LateFeeDebitNoteDraftFacts draft, LateFeeMonth month, Long dueDateVersionId,
                                    LatePaymentTerms dueDateTerms, LatePaymentTerms factDateTerms, Optional<LocalDate> enrollmentEndDate,
                                    NonNegativeMoney lateFeeAmount, LocalDate issueDate) {

    public LateFeeIssuingContext {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(dueDateVersionId, "dueDateVersionId");
        Objects.requireNonNull(dueDateTerms, "dueDateTerms");
        Objects.requireNonNull(factDateTerms, "factDateTerms");
        Objects.requireNonNull(enrollmentEndDate, "enrollmentEndDate");
        Objects.requireNonNull(lateFeeAmount, "lateFeeAmount");
        Objects.requireNonNull(issueDate, "issueDate");
    }

    public LocalDate debitNoteDueDate() {
        return issueDate.plusDays(dueDateTerms.lateFeeDebitNoteDueDays());
    }
}
