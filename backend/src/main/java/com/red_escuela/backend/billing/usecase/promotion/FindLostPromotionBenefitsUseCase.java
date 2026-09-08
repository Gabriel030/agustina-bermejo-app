package com.red_escuela.backend.billing.usecase.promotion;

import com.red_escuela.backend.billing.dto.LostPromotionBenefitResponse;

import java.util.List;

public interface FindLostPromotionBenefitsUseCase {
    List<LostPromotionBenefitResponse> find();
}
