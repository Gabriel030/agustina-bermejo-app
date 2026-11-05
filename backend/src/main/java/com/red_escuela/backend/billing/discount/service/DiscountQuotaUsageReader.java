package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.StudentDiscountOrigin;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import org.springframework.stereotype.Component;

@Component
public class DiscountQuotaUsageReader {
    private final StudentDiscountRepository repository;

    public DiscountQuotaUsageReader(StudentDiscountRepository repository) {
        this.repository = repository;
    }

    public long countPlacesUsed(Long discountDefinitionId) {
        return repository.countByDiscountDefinitionIdAndOrigin(discountDefinitionId, StudentDiscountOrigin.COHORT_ACQUISITION);
    }
}
