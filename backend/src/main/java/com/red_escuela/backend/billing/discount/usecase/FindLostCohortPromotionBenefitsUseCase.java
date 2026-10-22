package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.dto.LostPromotionBenefitResponse;

import java.util.List;

public interface FindLostCohortPromotionBenefitsUseCase {
    List<LostPromotionBenefitResponse> findLostBenefits();
}
