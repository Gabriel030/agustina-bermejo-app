package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record CollectionsStockRow(LocalDate businessDate, Currency currency, CollectionsStockScope scope, Map<OverdueAgeBucket, Money> overdueByBucket,
                                  long studentsWithOverdue) {

    public CollectionsStockRow {
        Objects.requireNonNull(businessDate, "businessDate");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(scope, "scope");
        if (studentsWithOverdue < 0) {
            throw new IllegalArgumentException("A count of students cannot be negative");
        }
        Map<OverdueAgeBucket, Money> complete = new EnumMap<>(OverdueAgeBucket.class);
        for (OverdueAgeBucket bucket : OverdueAgeBucket.values()) {
            Money amount = overdueByBucket.getOrDefault(bucket, Money.zero(currency));
            if (amount.currency() != currency) {
                throw new IllegalArgumentException("A stock row never mixes currencies: " + bucket + " is " + amount.currency() + " in a " + currency + " row");
            }
            if (amount.isNegative()) {
                throw new IllegalArgumentException("Overdue debt cannot be negative: " + bucket);
            }
            complete.put(bucket, amount);
        }
        overdueByBucket = Map.copyOf(complete);
    }

    public Money overdueIn(OverdueAgeBucket bucket) {
        return overdueByBucket.get(bucket);
    }

    public Money totalOverdue() {
        Money total = Money.zero(currency);
        for (Money amount : overdueByBucket.values()) {
            total = total.plus(amount);
        }
        return total;
    }
}
