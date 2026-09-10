package com.red_escuela.backend.billing.usecase.promotion;

import com.red_escuela.backend.billing.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.entity.Promotion;

public interface CreatePromotionForCourseUseCase {
    Promotion create(Long courseId, CreatePromotionRequest request);
}
