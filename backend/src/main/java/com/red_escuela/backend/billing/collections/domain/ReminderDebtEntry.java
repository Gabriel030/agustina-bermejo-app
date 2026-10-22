package com.red_escuela.backend.billing.collections.domain;

import java.util.Objects;

public record ReminderDebtEntry(ReminderInvoiceFact fact, ReminderConfigurationVersions versions, boolean underReview) {

    public ReminderDebtEntry {
        Objects.requireNonNull(fact, "fact");
        Objects.requireNonNull(versions, "versions");
    }

    public Long invoiceId() {
        return fact.invoiceId();
    }
}
