package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.exception.DiscountDefinitionNotFoundException;
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
        DiscountDefinition definition = repository.findById(promotionId).filter(found -> found.getDiscountType() == DiscountDefinitionType.COHORT_PROMOTION)
                .orElseThrow(() -> new DiscountDefinitionNotFoundException(promotionId));
        List<DiscountDefinition> active = repository.findCatalog(DiscountDefinitionType.COHORT_PROMOTION, DiscountDefinitionStatus.ACTIVE);
        List<DiscountDefinition> toClose = translator.findSiblingsOf(definition, active.stream().filter(candidate -> candidate.getStatus() == definition.getStatus()).toList());
        toClose.forEach(sibling -> operationRunner.runClosure(sibling.getId(), CLOSURE_REASON));
        if (toClose.isEmpty()) {
            operationRunner.runClosure(definition.getId(), CLOSURE_REASON);
        }
    }
}
