package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

import java.util.List;

public record PlannedReminderBlock(Currency currency, List<PlannedReminderLine> lines, Money total, Money availableCredit, Long shortfallInvoiceId, Money missingForNext) {

    public PlannedReminderBlock {
        lines = List.copyOf(lines);
    }
}
