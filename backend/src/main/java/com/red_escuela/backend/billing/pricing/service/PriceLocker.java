package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.exception.PriceNotFoundException;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import org.springframework.stereotype.Component;

@Component
public class PriceLocker {
    private final PriceRepository priceRepository;

    public PriceLocker(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    public Price lockForUpdate(Long priceId) {
        return priceRepository.findByIdForUpdate(priceId).orElseThrow(() -> new PriceNotFoundException(priceId));
    }
}
