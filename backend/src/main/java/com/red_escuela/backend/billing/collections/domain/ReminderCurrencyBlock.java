package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.List;

public record ReminderCurrencyBlock(Currency currency, List<ReminderDigestLine> lines, NonNegativeMoney total, NonNegativeMoney availableCredit,
                                    Long shortfallInvoiceId, NonNegativeMoney missingForNext) {

    public ReminderCurrencyBlock {
        lines = List.copyOf(lines);
    }

    public static ReminderCurrencyBlock of(Currency currency, List<ReminderDigestLine> lines, List<ReminderDigestLine> imputationOrderDebt, NonNegativeMoney availableCredit) {
        NonNegativeMoney zero = NonNegativeMoney.zero(currency);
        NonNegativeMoney total = lines.stream().map(ReminderDigestLine::balance).reduce(zero, NonNegativeMoney::plus);
        ReminderDigestLine next = imputationOrderDebt.stream().filter(line -> !line.receiptUnderReview()).findFirst().orElse(null);
        if (next == null || availableCredit.value().isZero()) {
            return new ReminderCurrencyBlock(currency, lines, total, availableCredit, null, zero);
        }
        NonNegativeMoney missing = next.balance().minusNotBelowZero(availableCredit);
        if (missing.value().isZero()) {
            return new ReminderCurrencyBlock(currency, lines, total, availableCredit, null, zero);
        }
        return new ReminderCurrencyBlock(currency, lines, total, availableCredit, next.invoiceId(), missing);
    }

    public boolean hasCreditShortfall() {
        return shortfallInvoiceId != null;
    }
}
