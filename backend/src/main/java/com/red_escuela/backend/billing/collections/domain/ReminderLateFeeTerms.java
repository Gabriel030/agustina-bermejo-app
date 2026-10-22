package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public record ReminderLateFeeTerms(Percentage dailyRate, int graceDays) {

    public ReminderLateFeeTerms {
        Objects.requireNonNull(dailyRate, "dailyRate");
        if (graceDays < 0) {
            throw new IllegalArgumentException("graceDays must not be negative");
        }
    }

    public static Optional<ReminderLateFeeTerms> of(LatePaymentTerms terms) {
        Objects.requireNonNull(terms, "terms");
        if (!terms.lateFeeChargingEnabled() || terms.dailyLateFeeRate().fraction().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new ReminderLateFeeTerms(terms.dailyLateFeeRate(), terms.gracePeriodDays()));
    }
}
