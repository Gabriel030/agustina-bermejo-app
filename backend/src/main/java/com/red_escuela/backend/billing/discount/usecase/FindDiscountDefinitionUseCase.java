package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionCatalogEntry;

public interface FindDiscountDefinitionUseCase {
    DiscountDefinitionCatalogEntry findDiscountDefinition(Long discountDefinitionId);
}
