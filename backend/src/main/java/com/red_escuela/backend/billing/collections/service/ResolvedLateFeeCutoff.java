package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.LateFeeCutoff;
import com.red_escuela.backend.billing.collections.domain.LateFeeSettlementSource;

import java.util.Objects;
import java.util.Optional;

public record ResolvedLateFeeCutoff(LateFeeCutoff cutoff, Optional<LateFeeSettlementSource> settlementSource) {

    public ResolvedLateFeeCutoff {
        Objects.requireNonNull(cutoff, "cutoff");
        Objects.requireNonNull(settlementSource, "settlementSource");
    }
}
