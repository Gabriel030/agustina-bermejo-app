package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.dto.PromotionResponse;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.service.PromotionContractTranslator;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListActivePromotionsByLegacyContractService implements ListActivePromotionsByLegacyContractUseCase {
    private final DiscountDefinitionRepository repository;
    private final PromotionContractTranslator translator;

    public ListActivePromotionsByLegacyContractService(DiscountDefinitionRepository repository, PromotionContractTranslator translator) {
        this.repository = repository;
        this.translator = translator;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<PromotionResponse> listActivePromotions() {
        return translator.toResponses(repository.findCatalog(DiscountDefinitionType.COHORT_PROMOTION, DiscountDefinitionStatus.ACTIVE));
    }
}
