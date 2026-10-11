package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.Objects;

public record ScheduledAllocation(Long invoiceId, NonNegativeMoney amount, LocalDate effectiveDate) {

    public ScheduledAllocation {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(effectiveDate, "effectiveDate");
    }
}
