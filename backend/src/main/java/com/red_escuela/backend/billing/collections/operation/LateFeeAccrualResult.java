package com.red_escuela.backend.billing.collections.operation;

import java.util.List;
import java.util.Map;

public record LateFeeAccrualResult(int candidates, int debitNotesIssued, int failed, List<Map<String, Object>> errors, int settlementDebitNotesRecovered) {

    public LateFeeAccrualResult(int candidates, int debitNotesIssued, int failed, List<Map<String, Object>> errors) {
        this(candidates, debitNotesIssued, failed, errors, 0);
    }

    public static LateFeeAccrualResult nothing() {
        return new LateFeeAccrualResult(0, 0, 0, List.of(), 0);
    }
}
