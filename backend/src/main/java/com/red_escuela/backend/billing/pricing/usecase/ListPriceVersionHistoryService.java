package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.pricing.dto.PriceVersionHistoryEntryResponse;
import com.red_escuela.backend.billing.pricing.exception.PriceNotFoundException;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListPriceVersionHistoryService implements ListPriceVersionHistoryUseCase {
    private final PriceRepository priceRepository;

    public ListPriceVersionHistoryService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<PriceVersionHistoryEntryResponse> listPriceVersionHistory(Long priceId) {
        return priceRepository.findById(priceId).orElseThrow(() -> new PriceNotFoundException(priceId)).versionsNewestFirst().stream()
                .map(PriceVersionHistoryEntryResponse::from).toList();
    }
}
