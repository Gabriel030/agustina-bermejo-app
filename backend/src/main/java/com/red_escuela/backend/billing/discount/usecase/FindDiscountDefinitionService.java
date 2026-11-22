package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionCatalogEntry;
import com.red_escuela.backend.billing.discount.service.DiscountDefinitionCatalogEntryAssembler;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class FindDiscountDefinitionService implements FindDiscountDefinitionUseCase {
    private final DiscountDefinitionCatalogEntryAssembler assembler;

    public FindDiscountDefinitionService(DiscountDefinitionCatalogEntryAssembler assembler) {
        this.assembler = assembler;
    }

    @Override
    public DiscountDefinitionCatalogEntry findDiscountDefinition(Long discountDefinitionId) {
        return assembler.findCatalogEntry(discountDefinitionId);
    }
}
