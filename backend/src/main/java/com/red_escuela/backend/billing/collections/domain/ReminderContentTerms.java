package com.red_escuela.backend.billing.collections.domain;

import java.util.Map;
import java.util.Optional;

public record ReminderContentTerms(Map<Long, ReminderLateFeeTerms> lateFeeByInvoiceId, ReminderSuspensionRule suspensionRule) {

    public ReminderContentTerms {
        lateFeeByInvoiceId = Map.copyOf(lateFeeByInvoiceId);
    }

    public static ReminderContentTerms none() {
        return new ReminderContentTerms(Map.of(), null);
    }

    public Optional<ReminderLateFeeTerms> findLateFeeOf(Long invoiceId) {
        return Optional.ofNullable(lateFeeByInvoiceId.get(invoiceId));
    }

    public Optional<ReminderSuspensionRule> findSuspensionRule() {
        return Optional.ofNullable(suspensionRule);
    }
}
