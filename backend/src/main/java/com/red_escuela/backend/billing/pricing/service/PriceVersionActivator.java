package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.entity.BatchExecutionLog;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class PriceVersionActivator {
    private static final Logger log = LoggerFactory.getLogger(PriceVersionActivator.class);
    private static final String JOB_NAME = "ActivateDuePriceVersionsJob";

    private final PriceRepository priceRepository;
    private final PriceLocker priceLocker;
    private final BatchExecutionLogRepository batchExecutionLogRepository;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate perPriceTransaction;
    private final Clock clock;

    public PriceVersionActivator(PriceRepository priceRepository, PriceLocker priceLocker, BatchExecutionLogRepository batchExecutionLogRepository,
                                 MeterRegistry meterRegistry, PlatformTransactionManager transactionManager, Clock clock) {
        this.priceRepository = priceRepository;
        this.priceLocker = priceLocker;
        this.batchExecutionLogRepository = batchExecutionLogRepository;
        this.meterRegistry = meterRegistry;
        this.perPriceTransaction = new TransactionTemplate(transactionManager);
        this.perPriceTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public PriceActivationOutcome activateDueVersions() {
        long start = System.currentTimeMillis();
        LocalDate today = LocalDate.now(clock);
        List<Long> priceIds = priceRepository.findIdsWithScheduledVersionsStartingOnOrBefore(today);
        int pricesActivated = 0;
        int versionsActivated = 0;
        List<String> errors = new ArrayList<>();
        for (Long priceId : priceIds) {
            try {
                versionsActivated += perPriceTransaction.execute(status -> activatePrice(priceId, today));
                pricesActivated++;
            } catch (RuntimeException failure) {
                log.error("Price version activation failed: price={}", priceId, failure);
                errors.add("price=" + priceId + ": " + failure.getMessage());
            }
        }
        meterRegistry.counter("billing_price_versions_activated_total").increment(versionsActivated);
        meterRegistry.counter("billing_price_version_activation_failed_total").increment(errors.size());
        if (!priceIds.isEmpty()) {
            BatchExecutionStatus status = errors.isEmpty() ? BatchExecutionStatus.SUCCESS
                    : (pricesActivated > 0 ? BatchExecutionStatus.PARTIAL_FAILURE : BatchExecutionStatus.FAILED);
            batchExecutionLogRepository.save(new BatchExecutionLog(JOB_NAME, LocalDateTime.now(clock), priceIds.size(), pricesActivated, errors.size(),
                    System.currentTimeMillis() - start, status, errors.isEmpty() ? null : String.join("; ", errors)));
        }
        return new PriceActivationOutcome(pricesActivated, versionsActivated, errors.size());
    }

    private int activatePrice(Long priceId, LocalDate today) {
        Price price = priceLocker.lockForUpdate(priceId);
        List<PriceVersion> due = price.activateDueVersions(today, Instant.now(clock));
        priceRepository.flush();
        due.forEach(version -> log.info("Price version effective: price={}, feeType={}, version={}", price.getId(), price.getFeeType(), version.getId()));
        return due.size();
    }
}
