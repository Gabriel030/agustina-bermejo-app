package com.red_escuela.backend.billing.collections.operation;

import java.util.List;
import java.util.Map;

public record LateFeeCorrectionSweepResult(int candidates, int corrected, int deferred, int failed, List<Map<String, Object>> errors) {

    public static LateFeeCorrectionSweepResult nothing() {
        return new LateFeeCorrectionSweepResult(0, 0, 0, 0, List.of());
    }
}
