package com.red_escuela.backend.shared.pricing;

import com.red_escuela.backend.shared.domain.Money;

public interface OfferingPriceCatalog {
    void registerInitialOfferingPrice(OfferingReference offering, OfferingFee fee, Money amount, Long actorPersonId);

    void adjustOfferingPrice(OfferingReference offering, OfferingFee fee, Money amount, Long actorPersonId);
}
