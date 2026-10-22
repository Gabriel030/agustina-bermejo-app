package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.discount.dto.PromotionResponse;
import com.red_escuela.backend.billing.discount.service.DiscountDefinitionOperationRunner;
import com.red_escuela.backend.billing.discount.service.PromotionContractTranslator;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class CreatePromotionByLegacyContractService implements CreatePromotionByLegacyContractUseCase {
    private final DiscountDefinitionOperationRunner operationRunner;
    private final PromotionContractTranslator translator;

    public CreatePromotionByLegacyContractService(DiscountDefinitionOperationRunner operationRunner, PromotionContractTranslator translator) {
        this.operationRunner = operationRunner;
        this.translator = translator;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('DIRECTOR')")
    public PromotionResponse createForProgram(Long programId, CreatePromotionRequest request) {
        return createAll(translator.toDraftsOfProgram(programId, request));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('DIRECTOR')")
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
