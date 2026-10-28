package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionCatalogEntry;
import com.red_escuela.backend.billing.discount.exception.DiscountDefinitionNotFoundException;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class DiscountDefinitionCatalogEntryAssembler {
    private final DiscountDefinitionRepository repository;
    private final DiscountQuotaCostEstimator costEstimator;
    private final DiscountQuotaUsageReader usageReader;
    private final Clock clock;

    public DiscountDefinitionCatalogEntryAssembler(DiscountDefinitionRepository repository, DiscountQuotaCostEstimator costEstimator, DiscountQuotaUsageReader usageReader, Clock clock) {
        this.repository = repository;
        this.costEstimator = costEstimator;
        this.usageReader = usageReader;
        this.clock = clock;
    }

    public DiscountDefinitionCatalogEntry findCatalogEntry(Long discountDefinitionId) {
        return repository.findById(discountDefinitionId).map(this::assembleCatalogEntry).orElseThrow(() -> new DiscountDefinitionNotFoundException(discountDefinitionId));
    }

    public DiscountDefinitionCatalogEntry assembleCatalogEntry(DiscountDefinition definition) {
        LocalDate today = LocalDate.now(clock);
        long placesUsed = usageReader.countPlacesUsed(definition.getId());
        return new DiscountDefinitionCatalogEntry(definition, definition.availabilityOn(today), costEstimator.estimateQuotaCostOn(definition, today), placesUsed,
                definition.cohortPromotionStatusOn(today, placesUsed));
    }
}
