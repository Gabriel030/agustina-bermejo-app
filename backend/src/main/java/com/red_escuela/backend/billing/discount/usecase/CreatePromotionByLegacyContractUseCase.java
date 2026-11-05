package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.discount.dto.PromotionResponse;

public interface CreatePromotionByLegacyContractUseCase {
    PromotionResponse createForProgram(Long programId, CreatePromotionRequest request);

    PromotionResponse createForCourse(Long courseId, CreatePromotionRequest request);
}
