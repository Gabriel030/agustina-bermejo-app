package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.LateFeeCutoff;

import java.util.Objects;
import java.util.Optional;

public record ResolvedLateFeeCutoff(LateFeeCutoff cutoff, Optional<Long> settlementAllocationId) {

    public ResolvedLateFeeCutoff {
        Objects.requireNonNull(cutoff, "cutoff");
        Objects.requireNonNull(settlementAllocationId, "settlementAllocationId");
    }
}
