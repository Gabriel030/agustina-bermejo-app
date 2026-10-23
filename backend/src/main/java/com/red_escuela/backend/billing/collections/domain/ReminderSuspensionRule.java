package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;

import java.util.Objects;
import java.util.Optional;

public record ReminderSuspensionRule(int afterDays, int afterInvoiceCount) {

    public ReminderSuspensionRule {
        if (afterDays < 0 || afterInvoiceCount < 0) {
            throw new IllegalArgumentException("The suspension thresholds must not be negative");
        }
    }

    public static Optional<ReminderSuspensionRule> of(LatePaymentTerms terms) {
        Objects.requireNonNull(terms, "terms");
        if (terms.suspensionAfterDays() == 0) {
            return Optional.empty();
        }
        return Optional.of(new ReminderSuspensionRule(terms.suspensionAfterDays(), terms.suspensionAfterInvoiceCount()));
    }
}
