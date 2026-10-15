package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CreditContributions(List<CreditContribution> contributions) {

    public CreditContributions {
        Objects.requireNonNull(contributions, "contributions");
        contributions = List.copyOf(contributions);
    }

    public static CreditContributions availableFrom(List<CurrentAccountEntry> entriesOldestFirst, Map<Long, NonNegativeMoney> netSourcedByEntryId,
                                                    NonNegativeMoney unallocatedCredit) {
        Set<Long> reversedEntryIds = new HashSet<>();
        entriesOldestFirst.stream().filter(entry -> entry.getEntryType() == CurrentAccountEntryType.PAYMENT_REVERSED)
                .forEach(entry -> reversedEntryIds.add(entry.getCompensatesEntryId()));
        Money zero = Money.zero(unallocatedCredit.value().currency());
        List<CreditContribution> remaining = new ArrayList<>();
        Money available = zero;
        for (CurrentAccountEntry entry : entriesOldestFirst) {
            if (!addsAllocatableCredit(entry.getEntryType()) || reversedEntryIds.contains(entry.getId())) {
                continue;
            }
            Money left = entry.getAllocatableAmount().value().minus(netSourcedByEntryId.getOrDefault(entry.getId(), NonNegativeMoney.of(zero)).value());
            if (left.isNegative()) {
                throw new IllegalStateException("Credit entry " + entry.getId() + " is taken beyond its allocatable amount");
            }
            if (!left.isZero()) {
                remaining.add(new CreditContribution(entry.getId(), entry.getPaymentId(), entry.getCreditNoteId(), entry.getEffectiveDate(), NonNegativeMoney.of(left)));
                available = available.plus(left);
            }
        }
        Money alreadyLeftTheAccount = available.minus(unallocatedCredit.value());
        List<CreditContribution> usable = new ArrayList<>();
        for (CreditContribution contribution : remaining) {
            Money left = contribution.amount().value();
            if (alreadyLeftTheAccount.isGreaterThan(zero)) {
                Money skipped = alreadyLeftTheAccount.isGreaterThan(left) ? left : alreadyLeftTheAccount;
                alreadyLeftTheAccount = alreadyLeftTheAccount.minus(skipped);
                left = left.minus(skipped);
            }
            if (!left.isZero()) {
                usable.add(new CreditContribution(contribution.entryId(), contribution.paymentId(), contribution.creditNoteId(), contribution.effectiveDate(),
                        NonNegativeMoney.of(left)));
            }
        }
        return new CreditContributions(usable);
    }

    public List<CreditContribution> asList() {
        return contributions;
    }

    private static boolean addsAllocatableCredit(CurrentAccountEntryType entryType) {
        return entryType == CurrentAccountEntryType.PAYMENT_RECEIVED || entryType == CurrentAccountEntryType.CREDIT_NOTE_ISSUED;
    }
}
