package com.red_escuela.backend.billing.discount.domain;

import java.util.Objects;
import java.util.Optional;

public record DiscountDefinitionCatalogEntry(DiscountDefinition definition, DiscountDefinitionAvailability availability, DiscountQuotaCostEstimate quotaCost, long quotaUsed,
                                             Optional<CohortPromotionStatus> cohortPromotionStatus) {

    public DiscountDefinitionCatalogEntry {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(quotaCost, "quotaCost");
        Objects.requireNonNull(cohortPromotionStatus, "cohortPromotionStatus");
    }
}
