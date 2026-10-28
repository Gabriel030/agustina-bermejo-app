package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.CohortPromotionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionCatalogEntry;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.service.DiscountDefinitionCatalogEntryAssembler;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListDiscountDefinitionsService implements ListDiscountDefinitionsUseCase {
    private final DiscountDefinitionRepository repository;
    private final DiscountDefinitionCatalogEntryAssembler assembler;

    public ListDiscountDefinitionsService(DiscountDefinitionRepository repository, DiscountDefinitionCatalogEntryAssembler assembler) {
        this.repository = repository;
        this.assembler = assembler;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<DiscountDefinitionCatalogEntry> listDiscountDefinitions(DiscountDefinitionType type, DiscountDefinitionStatus status, CohortPromotionStatus cohortPromotionStatus) {
        return repository.findCatalog(type, status).stream().map(assembler::assembleCatalogEntry)
                .filter(entry -> cohortPromotionStatus == null || entry.cohortPromotionStatus().filter(cohortPromotionStatus::equals).isPresent()).toList();
    }
}
