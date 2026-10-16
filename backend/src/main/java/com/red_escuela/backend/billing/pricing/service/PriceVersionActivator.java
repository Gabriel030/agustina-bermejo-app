package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class PriceVersionActivator {
    private static final Logger log = LoggerFactory.getLogger(PriceVersionActivator.class);

    private final PriceRepository priceRepository;
    private final Clock clock;

    public PriceVersionActivator(PriceRepository priceRepository, Clock clock) {
        this.priceRepository = priceRepository;
        this.clock = clock;
    }

    @Transactional
    public int activateDueVersions() {
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        int activated = 0;
        for (Price price : priceRepository.findWithScheduledVersionsStartingOnOrBefore(today)) {
            List<PriceVersion> due = price.activateDueVersions(today, now);
            priceRepository.flush();
            due.forEach(version -> log.info("Price version effective: price={}, feeType={}, version={}", price.getId(), price.getFeeType(), version.getId()));
            activated += due.size();
        }
        return activated;
    }
}
