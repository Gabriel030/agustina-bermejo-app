package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ReminderDigestLine(Long invoiceId, Long originInvoiceId, InvoiceType invoiceType, boolean debitNote, LocalDate dueDate, NonNegativeMoney balance,
                                 boolean receiptUnderReview, boolean precedesDueDate, List<ReminderDigestNotice> notices, ReminderConfigurationVersions versions) {

    public ReminderDigestLine {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(invoiceType, "invoiceType");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(versions, "versions");
        notices = List.copyOf(notices);
    }

    public static ReminderDigestLine of(ReminderDebtEntry entry, LocalDate sendDate, List<ReminderDigestNotice> notices) {
        ReminderInvoiceFact fact = entry.fact();
        return new ReminderDigestLine(fact.invoiceId(), fact.originInvoiceId(), fact.invoiceType(), fact.isDebitNote(), fact.dueDate(), fact.balance(),
                entry.underReview(), sendDate.isBefore(fact.dueDate()), notices, entry.versions());
    }

    public boolean isHighlighted() {
        return !notices.isEmpty();
    }

    public Currency currency() {
        return balance.value().currency();
    }
}
