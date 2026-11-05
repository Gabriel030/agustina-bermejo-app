package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.configuration.policy.ProposalApprovalCheck;
import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.events.PriceChangeDecidedEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.exception.PriceChangedConcurrentlyException;
import com.red_escuela.backend.billing.pricing.exception.PriceNotFoundException;
import com.red_escuela.backend.billing.pricing.exception.ProposalNoLongerOpenException;
import com.red_escuela.backend.billing.pricing.policy.ChangePriceCommand;
import com.red_escuela.backend.billing.pricing.policy.PriceChangeCheck;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import com.red_escuela.backend.shared.domain.Currency;
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
    private final PriceLocker priceLocker;
    private final BillingActorResolver actorResolver;
    private final Clock clock;

    public ChangePriceOperation(PriceRepository priceRepository, PriceVersionPolicyEvaluator policyEvaluator, PriceLocker priceLocker,
                                BillingActorResolver actorResolver, Clock clock) {
        this.priceLocker = priceLocker;
        this.priceRepository = priceRepository;
        this.policyEvaluator = policyEvaluator;
        this.actorResolver = actorResolver;
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
        return List.of("PRICE_CHANGE", "PRICE_VERSION", "PRICE_PROPOSAL", "PRICE_PROPOSAL_APPROVAL", "PRICE_PROPOSAL_REJECTION");
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(PriceChangeDecidedEvent.class);
    }

    @Override
    public void requireValidContext(ChangePriceCommand command) {
        Currency currency = priceRepository.findCurrencyById(command.priceId()).orElseThrow(() -> new PriceNotFoundException(command.priceId()));
        policyEvaluator.requireValidPriceChange(new PriceChangeCheck(command.effectiveFrom(), LocalDate.now(clock), command.changeReason()));
        policyEvaluator.requireValidPriceVersion(command.toDraft(currency));
    }

    @Override
    public PriceVersion processOperation(ChangePriceCommand command) {
        Price price = priceLocker.lockForUpdate(command.priceId());
        try {
            return command.requestedByDirector() ? publishDirectly(price, command) : approveProposal(price, command);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
            if (OfferingPriceCatalogService.isConcurrentPriceWrite(exception)) {
                throw new PriceChangedConcurrentlyException(price.getFeeType(), exception);
            }
            throw exception;
        }
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(ChangePriceCommand command, PriceVersion version) {
        if (command.requestedByDirector()) {
            return List.of();
        }
        return List.of(PriceChangeDecidedEvent.approvedFor(version.getId(), version.getPrice().getId(), version.getPrice().getDescription(), version.getEffectiveFrom(),
                version.getProposedById()));
    }

    private PriceVersion publishDirectly(Price price, ChangePriceCommand command) {
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        price.makeRoomForVersionStartingOn(command.effectiveFrom(), today, command.requestedByPersonId(), now);
        priceRepository.flush();
        PriceVersion version = price.publishPriceVersion(command.baseAmount(), command.marketingDiscount(), command.effectiveFrom(), command.changeReason(),
                command.requestedByPersonId(), false, today, now);
        priceRepository.flush();
        log.info("Price version {}: price={}, feeType={}, version={}, effectiveFrom={}, actor={}", version.getStatus(), price.getId(), price.getFeeType(),
                version.getId(), version.getEffectiveFrom(), command.requestedByPersonId());
        return version;
    }

    private PriceVersion approveProposal(Price price, ChangePriceCommand command) {
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        PriceVersion proposal = price.findOpenProposalOfRequest(command.authorizationRequestId()).orElseThrow(() -> new ProposalNoLongerOpenException(command.authorizationRequestId()));
        policyEvaluator.requireProposalNotExpired(new ProposalApprovalCheck(proposal.getEffectiveFrom(), today, proposal.getProposedAt().atZone(clock.getZone()).toLocalDate()));
        Long deciderId = actorResolver.currentPersonId();
        price.makeRoomForVersionStartingOn(proposal.getEffectiveFrom(), today, deciderId, now);
        priceRepository.flush();
        price.approveProposedVersion(proposal, deciderId, null, today, now);
        priceRepository.flush();
        log.info("Price proposal {}: price={}, feeType={}, version={}, effectiveFrom={}, proposedBy={}, decidedBy={}", proposal.getStatus(), price.getId(),
                price.getFeeType(), proposal.getId(), proposal.getEffectiveFrom(), proposal.getProposedById(), deciderId);
        return proposal;
    }
}
