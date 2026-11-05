package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.discount.dto.PromotionResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class PromotionContractCreation {

    private final DiscountDefinitionOperationRunner operationRunner;
    private final PromotionContractTranslator translator;

    public PromotionContractCreation(DiscountDefinitionOperationRunner operationRunner, PromotionContractTranslator translator) {
        this.operationRunner = operationRunner;
        this.translator = translator;
    }

    @Transactional
    public PromotionResponse createForProgram(Long programId, CreatePromotionRequest request) {
        return createAll(translator.toDraftsOfProgram(programId, request));
    }

    @Transactional
    public PromotionResponse createForCourse(Long courseId, CreatePromotionRequest request) {
        return createAll(translator.toDraftsOfCourse(courseId, request));
    }

    private PromotionResponse createAll(List<DiscountDefinitionDraft> drafts) {
        List<DiscountDefinition> created = drafts.stream()
                .map(draft -> operationRunner.runCreation(draft).resultOrThrowIfAwaitingAuthorization(requestId -> new IllegalStateException("Direct publication expected, got request " + requestId)))
                .toList();
        return translator.toResponses(created).get(0);
    }
}
