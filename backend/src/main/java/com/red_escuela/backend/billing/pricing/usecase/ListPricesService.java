package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.dto.PriceSummaryResponse;
import com.red_escuela.backend.billing.pricing.exception.PriceFilterConflictException;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@UseCase
public class ListPricesService implements ListPricesUseCase {
    private final PriceRepository priceRepository;
    private final Clock clock;

    public ListPricesService(PriceRepository priceRepository, Clock clock) {
        this.priceRepository = priceRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
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
