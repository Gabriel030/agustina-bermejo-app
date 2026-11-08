package com.red_escuela.backend.billing.advanceenrollmentfee.service;

import java.util.List;

public record AdvanceEnrollmentFeeExpiryResult(int expired, int skipped, List<Failure> failures) {

    public record Failure(Long invoiceId, String code, String message) {
    }

    public AdvanceEnrollmentFeeExpiryResult {
        failures = List.copyOf(failures);
    }

    public List<Long> failedInvoiceIds() {
        return failures.stream().map(Failure::invoiceId).toList();
    }

    public int candidates() {
        return expired + skipped + failures.size();
    }
}
