package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.pricing.dto.PriceSummaryResponse;

import java.util.List;

public interface ListPricesUseCase {
    List<PriceSummaryResponse> listPrices(Long programId, Long courseId);
}
