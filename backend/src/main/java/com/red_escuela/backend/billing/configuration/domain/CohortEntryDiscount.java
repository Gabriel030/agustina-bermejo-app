package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.shared.domain.Percentage;

import java.util.Objects;

public record CohortEntryDiscount(Percentage discount) implements ConfigurationValues {

    public CohortEntryDiscount {
        Objects.requireNonNull(discount, "discount");
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.COHORT_ENTRY;
    }
}
