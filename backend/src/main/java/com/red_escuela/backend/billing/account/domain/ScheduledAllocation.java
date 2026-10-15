package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ScheduledAllocation(Long invoiceId, NonNegativeMoney amount, LocalDate effectiveDate, List<AllocationSourceShare> sources) {

    public ScheduledAllocation {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        Objects.requireNonNull(sources, "sources");
        sources = List.copyOf(sources);
        Money sourced = sources.stream().map(share -> share.amount().value()).reduce(Money.zero(amount.value().currency()), Money::plus);
        if (!sourced.equals(amount.value())) {
            throw new IllegalArgumentException("The sources of an allocation must add up to its amount");
        }
    }
}
