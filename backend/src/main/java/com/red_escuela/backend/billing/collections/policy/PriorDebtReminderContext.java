package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;

import java.util.List;
import java.util.Objects;

public record PriorDebtReminderContext(ReminderInvoiceFact invoice, ReminderMessageType messageType, List<ReminderInvoiceFact> listedDebt) {

    public PriorDebtReminderContext {
        Objects.requireNonNull(invoice, "invoice");
        Objects.requireNonNull(messageType, "messageType");
        listedDebt = List.copyOf(listedDebt);
    }
}
