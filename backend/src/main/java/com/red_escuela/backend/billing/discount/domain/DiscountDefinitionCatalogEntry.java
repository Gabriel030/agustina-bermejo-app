package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.Objects;
import java.util.Optional;

public record DiscountDefinitionCatalogEntry(DiscountDefinition definition, DiscountDefinitionAvailability availability, Optional<NonNegativeMoney> estimatedQuotaCost, long quotaUsed) {

    public DiscountDefinitionCatalogEntry {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(estimatedQuotaCost, "estimatedQuotaCost");
    }
}
