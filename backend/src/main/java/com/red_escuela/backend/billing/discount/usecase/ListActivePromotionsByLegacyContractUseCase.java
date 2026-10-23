package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.dto.PromotionResponse;

import java.util.List;

public interface ListActivePromotionsByLegacyContractUseCase {
    List<PromotionResponse> listActivePromotions();
}
