package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.discount.dto.PromotionResponse;
import com.red_escuela.backend.billing.discount.service.PromotionContractCreation;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreatePromotionByLegacyContractService implements CreatePromotionByLegacyContractUseCase {

    private final PromotionContractCreation promotionCreation;

    public CreatePromotionByLegacyContractService(PromotionContractCreation promotionCreation) {
        this.promotionCreation = promotionCreation;
    }

    @Override
    public PromotionResponse createForProgram(Long programId, CreatePromotionRequest request) {
        return promotionCreation.createForProgram(programId, request);
    }

    @Override
    public PromotionResponse createForCourse(Long courseId, CreatePromotionRequest request) {
        return promotionCreation.createForCourse(courseId, request);
    }
}
