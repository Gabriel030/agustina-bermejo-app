package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.Objects;
import java.util.Optional;

public record DiscountQuotaCostEstimate(DiscountQuotaCostStatus status, Optional<NonNegativeMoney> cost) {

    public DiscountQuotaCostEstimate {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(cost, "cost");
    }

    public static DiscountQuotaCostEstimate estimated(NonNegativeMoney cost) {
        return new DiscountQuotaCostEstimate(DiscountQuotaCostStatus.ESTIMATED, Optional.of(cost));
    }

    public static DiscountQuotaCostEstimate withoutCost(DiscountQuotaCostStatus status) {
        return new DiscountQuotaCostEstimate(status, Optional.empty());
    }
}
