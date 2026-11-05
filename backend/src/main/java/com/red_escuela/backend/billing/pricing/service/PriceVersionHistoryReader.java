package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.pricing.dto.PriceVersionHistoryEntryResponse;
import com.red_escuela.backend.billing.pricing.exception.PriceNotFoundException;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PriceVersionHistoryReader {
    private final PriceRepository priceRepository;

    public PriceVersionHistoryReader(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    @Transactional(readOnly = true)
    public List<PriceVersionHistoryEntryResponse> listPriceVersionHistory(Long priceId) {
        return priceRepository.findById(priceId).orElseThrow(() -> new PriceNotFoundException(priceId)).versionsNewestFirst().stream()
                .map(PriceVersionHistoryEntryResponse::from).toList();
    }
}
