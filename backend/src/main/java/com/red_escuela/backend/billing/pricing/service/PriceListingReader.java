package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.dto.PriceSummaryResponse;
import com.red_escuela.backend.billing.pricing.exception.PriceFilterConflictException;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PriceListingReader {
    private final PriceRepository priceRepository;
    private final Clock clock;

    public PriceListingReader(PriceRepository priceRepository, Clock clock) {
        this.priceRepository = priceRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PriceSummaryResponse> listPrices(Long programId, Long courseId) {
        if (programId != null && courseId != null) {
            throw new PriceFilterConflictException();
        }
        LocalDate today = LocalDate.now(clock);
        return findPrices(programId, courseId).stream().map(price -> PriceSummaryResponse.from(price, today)).toList();
    }

    private List<Price> findPrices(Long programId, Long courseId) {
        if (programId != null) {
            return priceRepository.findByProgramIdOrderByIdAsc(programId);
        }
        if (courseId != null) {
            return priceRepository.findByCourseIdOrderByIdAsc(courseId);
        }
        return priceRepository.findAllByOrderByIdAsc();
    }
}
