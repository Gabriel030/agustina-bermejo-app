package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.pricing.dto.PriceSummaryResponse;
import com.red_escuela.backend.billing.pricing.service.PriceListingReader;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class ListPricesService implements ListPricesUseCase {

    private final PriceListingReader priceListingReader;

    public ListPricesService(PriceListingReader priceListingReader) {
        this.priceListingReader = priceListingReader;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<PriceSummaryResponse> listPrices(Long programId, Long courseId) {
        return priceListingReader.listPrices(programId, courseId);
    }
}
