package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.domain.PromotionClosureTarget;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromotionContractCloser {
    private static final String CLOSURE_REASON = "Cierre de la promoción";

    private final DiscountDefinitionRepository repository;
    private final DiscountDefinitionOperationRunner operationRunner;
    private final PromotionContractTranslator translator;

    public PromotionContractCloser(DiscountDefinitionRepository repository, DiscountDefinitionOperationRunner operationRunner, PromotionContractTranslator translator) {
        this.repository = repository;
        this.operationRunner = operationRunner;
        this.translator = translator;
    }

    public void closePromotionAndItsSiblings(Long promotionId) {
        DiscountDefinition definition = PromotionClosureTarget.requireOpenPromotion(promotionId, repository.findById(promotionId)).definition();
        List<DiscountDefinition> active = repository.findCatalog(DiscountDefinitionType.COHORT_PROMOTION, DiscountDefinitionStatus.ACTIVE);
        translator.findSiblingsOf(definition, active).forEach(sibling -> operationRunner.runClosure(sibling.getId(), CLOSURE_REASON));
    }
}
