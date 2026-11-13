package com.red_escuela.backend.shared.pricing;

import com.red_escuela.backend.shared.domain.Money;
import java.time.LocalDate;
import java.util.Optional;

public interface OfferingPriceResolver {
    Optional<Money> findListPriceOn(OfferingReference offering, OfferingFee fee, LocalDate date);

    Optional<Money> findCurrentListPrice(OfferingReference offering, OfferingFee fee);
}
