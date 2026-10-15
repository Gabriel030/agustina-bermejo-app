package com.red_escuela.backend.billing.discount.domain;

import java.util.Objects;

public record DiscountDefinitionCatalogEntry(DiscountDefinition definition, DiscountDefinitionAvailability availability, DiscountQuotaCostEstimate quotaCost, long quotaUsed) {

    public DiscountDefinitionCatalogEntry {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(quotaCost, "quotaCost");
    }
}
