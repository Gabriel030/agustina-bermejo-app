package com.red_escuela.backend.billing.annualupfront.service;

import java.util.List;

public record AnnualUpfrontExpiryResult(int expired, int skipped, List<Long> failedRequestIds) {

    public AnnualUpfrontExpiryResult {
        failedRequestIds = List.copyOf(failedRequestIds);
    }

    public boolean isPartialFailure() {
        return !failedRequestIds.isEmpty();
    }
}
