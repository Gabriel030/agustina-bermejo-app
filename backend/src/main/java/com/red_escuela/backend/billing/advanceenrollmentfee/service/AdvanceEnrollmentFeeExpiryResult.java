package com.red_escuela.backend.billing.advanceenrollmentfee.service;

import java.util.List;

public record AdvanceEnrollmentFeeExpiryResult(int expired, int skipped, List<Long> failedInvoiceIds) {

    public AdvanceEnrollmentFeeExpiryResult {
        failedInvoiceIds = List.copyOf(failedInvoiceIds);
    }

    public int candidates() {
        return expired + skipped + failedInvoiceIds.size();
    }
}
