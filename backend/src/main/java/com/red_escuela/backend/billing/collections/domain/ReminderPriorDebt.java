package com.red_escuela.backend.billing.collections.domain;

import java.util.List;
import java.util.Objects;

public final class ReminderPriorDebt {

    private ReminderPriorDebt() {
    }

    public static boolean existsBefore(ReminderInvoiceFact invoice, List<ReminderInvoiceFact> listedDebt) {
        Objects.requireNonNull(invoice, "invoice");
        return listedDebt.stream().anyMatch(other -> !other.invoiceId().equals(invoice.invoiceId()) && other.issueDate().isBefore(invoice.issueDate()));
    }
}
