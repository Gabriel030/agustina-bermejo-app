package com.red_escuela.backend.billing.collections.domain;

import java.util.Objects;
import java.util.Optional;

public record LateFeeSettlementSource(Optional<Long> allocationId, Optional<Long> creditNoteId) {

    public LateFeeSettlementSource {
        Objects.requireNonNull(allocationId, "allocationId");
        Objects.requireNonNull(creditNoteId, "creditNoteId");
        if (allocationId.isPresent() == creditNoteId.isPresent()) {
            throw new IllegalArgumentException("A settlement comes from exactly one of an allocation or a credit note");
        }
    }

    public static LateFeeSettlementSource ofAllocation(Long allocationId) {
        return new LateFeeSettlementSource(Optional.of(Objects.requireNonNull(allocationId, "allocationId")), Optional.empty());
    }

    public static LateFeeSettlementSource ofCreditNote(Long creditNoteId) {
        return new LateFeeSettlementSource(Optional.empty(), Optional.of(Objects.requireNonNull(creditNoteId, "creditNoteId")));
    }
}
