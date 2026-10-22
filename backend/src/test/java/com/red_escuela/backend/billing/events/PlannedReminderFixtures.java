package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;

public final class PlannedReminderFixtures {

    private PlannedReminderFixtures() {
    }

    public static PlannedReminderLine line(long invoiceId, LocalDate dueDate, Money balance, boolean precedesDueDate, PlannedReminderNoticeType... noticeTypes) {
        List<PlannedReminderNotice> notices = java.util.Arrays.stream(noticeTypes).map(type -> new PlannedReminderNotice("notice-" + type.name(), type)).toList();
        return new PlannedReminderLine(invoiceId, null, PlannedReminderInvoiceKind.MONTHLY_INSTALLMENT, false, dueDate, balance, false, precedesDueDate, notices,
                1L, null, null, 2L);
    }

    public static PlannedReminderLine receiptUnderReviewLine(long invoiceId, LocalDate dueDate, Money balance) {
        return new PlannedReminderLine(invoiceId, null, PlannedReminderInvoiceKind.MONTHLY_INSTALLMENT, false, dueDate, balance, true, false, List.of(), 1L, null, null, 2L);
    }

    public static PlannedReminderBlock block(Currency currency, Money total, Money credit, Long shortfallInvoiceId, Money missing, PlannedReminderLine... lines) {
        return new PlannedReminderBlock(currency, List.of(lines), total, credit, shortfallInvoiceId, missing);
    }

    public static PlannedReminderBlock arsBlock(PlannedReminderLine... lines) {
        Money total = java.util.Arrays.stream(lines).map(PlannedReminderLine::balance).reduce(Money.zero(Currency.ARS), Money::plus);
        return block(Currency.ARS, total, Money.zero(Currency.ARS), null, Money.zero(Currency.ARS), lines);
    }

    public static PlannedReminderMessage digest(PlannedReminderNoticeType lead, boolean precedesDueDate, PlannedReminderBlock... blocks) {
        return new PlannedReminderMessage(PlannedReminderFormat.DAILY_DIGEST, lead, lead.dispatchPriority(), precedesDueDate, List.of(blocks));
    }

    public static PlannedReminderMessage ownNotice(PlannedReminderNoticeType lead, boolean precedesDueDate, PlannedReminderBlock... blocks) {
        return new PlannedReminderMessage(PlannedReminderFormat.OWN_NOTICE, lead, lead.dispatchPriority(), precedesDueDate, List.of(blocks));
    }

    public static Money hundredThousand() {
        return ars(100000);
    }
}
