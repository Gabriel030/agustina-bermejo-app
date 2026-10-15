package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionCatalogEntry;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;

import java.util.List;

public interface ListDiscountDefinitionsUseCase {
    List<DiscountDefinitionCatalogEntry> listDiscountDefinitions(DiscountDefinitionType type, DiscountDefinitionStatus status);
}
