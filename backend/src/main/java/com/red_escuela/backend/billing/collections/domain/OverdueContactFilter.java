package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Currency;

public record OverdueContactFilter(Currency currency, Integer minDaysOverdue, Integer minOverdueInstallments, boolean onlyNewlyListed,
                                   OverdueContactReason entryReason) {

    public static OverdueContactFilter none() {
        return new OverdueContactFilter(null, null, null, false, null);
    }

    public static OverdueContactFilter byCurrency(Currency currency) {
        return new OverdueContactFilter(currency, null, null, false, null);
    }

    public boolean accepts(ListedOverdueContact listed) {
        OverdueContact contact = listed.contact();
        return (currency == null || contact.owesIn(currency))
                && (minDaysOverdue == null || contact.oldestDaysOverdue() >= minDaysOverdue)
                && (minOverdueInstallments == null || contact.overdueInstallments() >= minOverdueInstallments)
                && (!onlyNewlyListed || listed.newlyListed())
                && (entryReason == null || listed.entryReason() == entryReason);
    }
}
