package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionCatalogEntry;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.service.DiscountQuotaCostEstimator;
import com.red_escuela.backend.billing.discount.service.DiscountQuotaUsageReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@UseCase
public class ListDiscountDefinitionsService implements ListDiscountDefinitionsUseCase {
    private final DiscountDefinitionRepository repository;
    private final DiscountQuotaCostEstimator costEstimator;
    private final DiscountQuotaUsageReader usageReader;
    private final Clock clock;

    public ListDiscountDefinitionsService(DiscountDefinitionRepository repository, DiscountQuotaCostEstimator costEstimator, DiscountQuotaUsageReader usageReader,
                                          Clock clock) {
        this.repository = repository;
        this.costEstimator = costEstimator;
        this.usageReader = usageReader;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<DiscountDefinitionCatalogEntry> listDiscountDefinitions(DiscountDefinitionType type, DiscountDefinitionStatus status) {
        LocalDate today = LocalDate.now(clock);
        return repository.findCatalog(type, status).stream().map(definition -> catalogEntryOf(definition, today)).toList();
    }

    private DiscountDefinitionCatalogEntry catalogEntryOf(DiscountDefinition definition, LocalDate today) {
        return new DiscountDefinitionCatalogEntry(definition, definition.availabilityOn(today), costEstimator.estimateQuotaCostOn(definition, today),
                usageReader.countPlacesUsed(definition.getId()));
    }
}
