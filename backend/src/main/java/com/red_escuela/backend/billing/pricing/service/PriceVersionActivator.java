package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class PriceVersionActivator {
    private static final Logger log = LoggerFactory.getLogger(PriceVersionActivator.class);

    private final PriceRepository priceRepository;
    private final PriceLocker priceLocker;
    private final TransactionTemplate perPriceTransaction;
    private final Clock clock;

    public PriceVersionActivator(PriceRepository priceRepository, PriceLocker priceLocker, PlatformTransactionManager transactionManager, Clock clock) {
        this.priceRepository = priceRepository;
        this.priceLocker = priceLocker;
        this.perPriceTransaction = new TransactionTemplate(transactionManager);
        this.perPriceTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public int activateDueVersions() {
        LocalDate today = LocalDate.now(clock);
        int activated = 0;
        for (Long priceId : priceRepository.findIdsWithScheduledVersionsStartingOnOrBefore(today)) {
            try {
                activated += perPriceTransaction.execute(status -> activatePrice(priceId, today));
            } catch (RuntimeException failure) {
                log.error("Price version activation failed: price={}", priceId, failure);
            }
        }
        return activated;
    }

    private int activatePrice(Long priceId, LocalDate today) {
        Price price = priceLocker.lockForUpdate(priceId);
        List<PriceVersion> due = price.activateDueVersions(today, Instant.now(clock));
        priceRepository.flush();
        due.forEach(version -> log.info("Price version effective: price={}, feeType={}, version={}", price.getId(), price.getFeeType(), version.getId()));
        return due.size();
    }
}
