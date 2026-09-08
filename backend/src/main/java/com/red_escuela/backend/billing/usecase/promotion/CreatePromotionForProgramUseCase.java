package com.red_escuela.backend.billing.usecase.promotion;

import com.red_escuela.backend.billing.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.entity.Promotion;

public interface CreatePromotionForProgramUseCase {
    Promotion create(Long programId, CreatePromotionRequest request);
}
