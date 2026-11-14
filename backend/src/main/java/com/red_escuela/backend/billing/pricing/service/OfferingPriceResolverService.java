package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionResolution;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.pricing.OfferingFee;
import com.red_escuela.backend.shared.pricing.OfferingPriceResolver;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class OfferingPriceResolverService implements OfferingPriceResolver {
    private final PriceReader priceReader;
    private final Clock clock;

    public OfferingPriceResolverService(PriceReader priceReader, Clock clock) {
        this.priceReader = priceReader;
        this.clock = clock;
    }

    @Override
    public Optional<Money> findListPriceOn(OfferingReference offering, OfferingFee fee, LocalDate date) {
        return priceReader.findPriceVersionEffectiveOn(offering, FeeType.valueOf(fee.name()), date)
                .map(PriceVersionResolution::listAmount)
                .map(amount -> amount.value());
    }

    @Override
    public Optional<Money> findCurrentListPrice(OfferingReference offering, OfferingFee fee) {
        return findListPriceOn(offering, fee, LocalDate.now(clock));
    }

    @Override
    public Map<Long, Money> findCurrentListPrices(Collection<Long> offeringIds, boolean programs, OfferingFee fee) {
        Map<Long, Money> prices = new HashMap<>();
        priceReader.findPriceVersionsEffectiveOn(offeringIds, programs, FeeType.valueOf(fee.name()), LocalDate.now(clock))
                .forEach((offeringId, resolution) -> prices.put(offeringId, resolution.listAmount().value()));
        return prices;
    }
}
