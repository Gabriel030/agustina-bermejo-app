package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.exception.PriceChangedConcurrentlyException;
import com.red_escuela.backend.billing.pricing.exception.PriceNotFoundException;
import com.red_escuela.backend.billing.pricing.policy.ChangePriceCommand;
import com.red_escuela.backend.billing.pricing.policy.PriceChangeCheck;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
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
public class ChangePriceOperation implements BillingOperation<ChangePriceCommand, PriceVersion> {
    private static final Logger log = LoggerFactory.getLogger(ChangePriceOperation.class);

    private final PriceRepository priceRepository;
    private final PriceVersionPolicyEvaluator policyEvaluator;
    private final Clock clock;

    public ChangePriceOperation(PriceRepository priceRepository, PriceVersionPolicyEvaluator policyEvaluator, Clock clock) {
        this.priceRepository = priceRepository;
        this.policyEvaluator = policyEvaluator;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CHANGE_PRICE;
    }

    @Override
    public Class<ChangePriceCommand> contextType() {
        return ChangePriceCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of("PRICE_CHANGE", "PRICE_VERSION");
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of();
    }

    @Override
    public void requireValidContext(ChangePriceCommand command) {
        Price price = findPrice(command);
        policyEvaluator.requireValidPriceChange(new PriceChangeCheck(command.effectiveFrom(), LocalDate.now(clock), command.changeReason()));
        policyEvaluator.requireValidPriceVersion(command.toDraft(price.getCurrency()));
    }

    @Override
    public PriceVersion processOperation(ChangePriceCommand command) {
        Price price = findPrice(command);
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        try {
            price.makeRoomForVersionStartingOn(command.effectiveFrom(), today, command.publisherPersonId(), now);
            priceRepository.flush();
            PriceVersion version = price.publishPriceVersion(command.baseAmount(), command.marketingDiscount(), command.effectiveFrom(), command.changeReason(),
                    command.publisherPersonId(), false, today, now);
            priceRepository.flush();
            log.info("Price version {}: price={}, feeType={}, version={}, effectiveFrom={}, actor={}", version.getStatus(), price.getId(), price.getFeeType(),
                    version.getId(), version.getEffectiveFrom(), command.publisherPersonId());
            return version;
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
            if (OfferingPriceCatalogService.isConcurrentPriceWrite(exception)) {
                throw new PriceChangedConcurrentlyException(price.getFeeType(), exception);
            }
            throw exception;
        }
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(ChangePriceCommand command, PriceVersion version) {
        return List.of();
    }

    private Price findPrice(ChangePriceCommand command) {
        return priceRepository.findById(command.priceId()).orElseThrow(() -> new PriceNotFoundException(command.priceId()));
    }
}
