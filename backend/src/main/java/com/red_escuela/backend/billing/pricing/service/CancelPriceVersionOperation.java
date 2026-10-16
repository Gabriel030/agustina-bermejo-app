package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.exception.PriceChangedConcurrentlyException;
import com.red_escuela.backend.billing.pricing.exception.PriceVersionNotFoundException;
import com.red_escuela.backend.billing.pricing.policy.CancelPriceVersionCommand;
import com.red_escuela.backend.billing.pricing.policy.PriceVersionCancellationCheck;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import com.red_escuela.backend.billing.pricing.repository.PriceVersionCancellationState;
import com.red_escuela.backend.billing.pricing.repository.PriceVersionRepository;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class CancelPriceVersionOperation implements BillingOperation<CancelPriceVersionCommand, PriceVersion> {
    private static final Logger log = LoggerFactory.getLogger(CancelPriceVersionOperation.class);

    private final PriceRepository priceRepository;
    private final PriceVersionRepository priceVersionRepository;
    private final PriceVersionPolicyEvaluator policyEvaluator;
    private final PriceLocker priceLocker;
    private final Clock clock;

    public CancelPriceVersionOperation(PriceRepository priceRepository, PriceVersionRepository priceVersionRepository,
                                       PriceVersionPolicyEvaluator policyEvaluator, PriceLocker priceLocker, Clock clock) {
        this.priceLocker = priceLocker;
        this.priceRepository = priceRepository;
        this.priceVersionRepository = priceVersionRepository;
        this.policyEvaluator = policyEvaluator;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CANCEL_PRICE_VERSION;
    }

    @Override
    public Class<CancelPriceVersionCommand> contextType() {
        return CancelPriceVersionCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of("PRICE_VERSION_CANCELLATION");
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of();
    }

    @Override
    public void requireValidContext(CancelPriceVersionCommand command) {
        PriceVersionCancellationState state = findState(command);
        policyEvaluator.requireCancellablePriceVersion(new PriceVersionCancellationCheck(state.status(), state.effectiveFrom(), LocalDate.now(clock), command.reason()));
    }

    @Override
    public PriceVersion processOperation(CancelPriceVersionCommand command) {
        Price price = priceLocker.lockForUpdate(findState(command).priceId());
        PriceVersion version = price.getVersions().stream().filter(candidate -> candidate.getId().equals(command.priceVersionId())).findFirst()
                .orElseThrow(() -> new PriceVersionNotFoundException(command.priceVersionId()));
        policyEvaluator.requireCancellablePriceVersion(new PriceVersionCancellationCheck(version.getStatus(), version.getEffectiveFrom(), LocalDate.now(clock), command.reason()));
        try {
            price.cancelScheduledVersion(version, command.cancelledByPersonId(), command.reason(), Instant.now(clock));
            priceRepository.flush();
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
            if (OfferingPriceCatalogService.isConcurrentPriceWrite(exception)) {
                throw new PriceChangedConcurrentlyException(price.getFeeType(), exception);
            }
            throw exception;
        }
        log.info("Price version CANCELLED: price={}, feeType={}, version={}, effectiveFrom={}, actor={}", price.getId(), price.getFeeType(), version.getId(),
                version.getEffectiveFrom(), command.cancelledByPersonId());
        return version;
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(CancelPriceVersionCommand command, PriceVersion version) {
        return List.of();
    }

    private PriceVersionCancellationState findState(CancelPriceVersionCommand command) {
        return priceVersionRepository.findCancellationStateById(command.priceVersionId()).orElseThrow(() -> new PriceVersionNotFoundException(command.priceVersionId()));
    }
}
