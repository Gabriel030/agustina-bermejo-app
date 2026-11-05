package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.pricing.dto.PriceVersionHistoryEntryResponse;
import com.red_escuela.backend.billing.pricing.service.PriceVersionHistoryReader;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class ListPriceVersionHistoryService implements ListPriceVersionHistoryUseCase {

    private final PriceVersionHistoryReader priceVersionHistoryReader;

    public ListPriceVersionHistoryService(PriceVersionHistoryReader priceVersionHistoryReader) {
        this.priceVersionHistoryReader = priceVersionHistoryReader;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<PriceVersionHistoryEntryResponse> listPriceVersionHistory(Long priceId) {
        return priceVersionHistoryReader.listPriceVersionHistory(priceId);
    }
}
