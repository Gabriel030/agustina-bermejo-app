package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.Objects;

public record CreditContribution(Long entryId, Long paymentId, Long creditNoteId, LocalDate effectiveDate, NonNegativeMoney amount) {

    public CreditContribution {
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        Objects.requireNonNull(amount, "amount");
    }

    public CreditContribution(LocalDate effectiveDate, NonNegativeMoney amount) {
        this(null, null, null, effectiveDate, amount);
    }
}
