package com.red_escuela.backend.billing.discount.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionAvailability;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionCatalogEntry;
import com.red_escuela.backend.shared.domain.Money;

public record DiscountDefinitionCatalogEntryResponse(@JsonUnwrapped DiscountDefinitionResponse definition, DiscountDefinitionAvailability availability,
                                                     Money estimatedQuotaCost, long quotaUsed) {

    public static DiscountDefinitionCatalogEntryResponse from(DiscountDefinitionCatalogEntry entry) {
        return new DiscountDefinitionCatalogEntryResponse(DiscountDefinitionResponse.from(entry.definition()), entry.availability(),
                entry.estimatedQuotaCost().map(cost -> cost.value()).orElse(null), entry.quotaUsed());
    }
}
