package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.Objects;

public record AllocationSourceShare(Long creditEntryId, Long paymentId, Long creditNoteId, NonNegativeMoney amount) {

    public AllocationSourceShare {
        Objects.requireNonNull(creditEntryId, "creditEntryId");
        Objects.requireNonNull(amount, "amount");
        if ((paymentId == null) == (creditNoteId == null)) {
            throw new IllegalArgumentException("A source comes from exactly one payment or credit note");
        }
        if (amount.value().isZero()) {
            throw new IllegalArgumentException("A source amount must be greater than zero");
        }
    }
}
