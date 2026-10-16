package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.pricing.dto.PriceVersionHistoryEntryResponse;

import java.util.List;

public interface ListPriceVersionHistoryUseCase {
    List<PriceVersionHistoryEntryResponse> listPriceVersionHistory(Long priceId);
}
