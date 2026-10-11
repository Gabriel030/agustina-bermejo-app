package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.List;
import java.util.Objects;

public record CreditContributions(List<CreditContribution> contributions) {

    public CreditContributions {
        Objects.requireNonNull(contributions, "contributions");
        contributions = List.copyOf(contributions);
    }

    public static CreditContributions fromEntriesOldestFirst(List<CurrentAccountEntry> entriesOldestFirst) {
        return new CreditContributions(entriesOldestFirst.stream()
                .filter(entry -> addsAllocatableCredit(entry.getEntryType()))
                .map(entry -> new CreditContribution(entry.getEffectiveDate(), entry.getAmount()))
                .toList());
    }

    public NonNegativeMoney consumedCreditGiven(NonNegativeMoney unallocatedCredit) {
        Money creditedInTotal = contributions.stream().map(credit -> credit.amount().value())
                .reduce(Money.zero(unallocatedCredit.value().currency()), Money::plus);
        Money alreadyConsumed = creditedInTotal.minus(unallocatedCredit.value());
        return alreadyConsumed.isNegative() ? NonNegativeMoney.zero(unallocatedCredit.value().currency()) : NonNegativeMoney.of(alreadyConsumed);
    }

    public List<CreditContribution> asList() {
        return contributions;
    }

    private static boolean addsAllocatableCredit(CurrentAccountEntryType entryType) {
        return entryType == CurrentAccountEntryType.PAYMENT_RECEIVED || entryType == CurrentAccountEntryType.CREDIT_NOTE_ISSUED;
    }
}
