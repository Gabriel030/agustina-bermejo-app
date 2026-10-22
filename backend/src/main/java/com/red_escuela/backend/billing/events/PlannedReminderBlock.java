package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

import java.util.List;
import java.util.Optional;

public record PlannedReminderBlock(Currency currency, List<PlannedReminderLine> lines, Money total, Money availableCredit, Long shortfallInvoiceId, Money missingForNext) {

    public PlannedReminderBlock {
        lines = List.copyOf(lines);
    }

    public boolean hasCreditShortfall() {
        return shortfallInvoiceId != null;
    }

    public Optional<PlannedReminderBlock> withoutLinesPrecedingTheirDueDate() {
        List<PlannedReminderLine> remaining = lines.stream().filter(line -> !line.precedesDueDate()).toList();
        if (remaining.isEmpty()) {
            return Optional.empty();
        }
        Money remainingTotal = remaining.stream().map(PlannedReminderLine::balance).reduce(Money.zero(currency), Money::plus);
        boolean shortfallInvoiceRemoved = shortfallInvoiceId != null && lines.stream().anyMatch(line -> shortfallInvoiceId.equals(line.invoiceId()) && line.precedesDueDate());
        if (!shortfallInvoiceRemoved) {
            return Optional.of(new PlannedReminderBlock(currency, remaining, remainingTotal, availableCredit, shortfallInvoiceId, missingForNext));
        }
        return Optional.of(recomputedShortfallOver(remaining, remainingTotal));
    }

    private PlannedReminderBlock recomputedShortfallOver(List<PlannedReminderLine> remaining, Money remainingTotal) {
        Money zero = Money.zero(currency);
        PlannedReminderLine next = remaining.stream().filter(line -> !line.receiptUnderReview()).findFirst().orElse(null);
        if (next == null || availableCredit.isZero()) {
            return new PlannedReminderBlock(currency, remaining, remainingTotal, availableCredit, null, zero);
        }
        Money missing = next.balance().minus(availableCredit);
        if (missing.isNegative() || missing.isZero()) {
            return new PlannedReminderBlock(currency, remaining, remainingTotal, availableCredit, null, zero);
        }
        return new PlannedReminderBlock(currency, remaining, remainingTotal, availableCredit, next.invoiceId(), missing);
    }
}
