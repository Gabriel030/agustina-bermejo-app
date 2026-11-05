package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.events.PriceChangeDecidedEvent;
import com.red_escuela.backend.billing.operation.AuthorizationRequestObserver;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.policy.ChangePriceCommand;
import com.red_escuela.backend.billing.pricing.policy.PriceProposalSlot;
import com.red_escuela.backend.billing.pricing.policy.PriceRejectionCheck;
import com.red_escuela.backend.billing.pricing.repository.PriceVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Component
public class PriceProposalObserver implements AuthorizationRequestObserver {
    private static final Logger log = LoggerFactory.getLogger(PriceProposalObserver.class);
    private static final String REJECTED_ON_APPROVAL = "Rechazada al aprobar: ";

    private final PriceVersionRepository versionRepository;
    private final PriceLocker priceLocker;
    private final PriceVersionPolicyEvaluator policyEvaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PriceProposalObserver(PriceVersionRepository versionRepository, PriceLocker priceLocker, PriceVersionPolicyEvaluator policyEvaluator,
                                 ApplicationEventPublisher eventPublisher, Clock clock) {
        this.versionRepository = versionRepository;
        this.priceLocker = priceLocker;
        this.policyEvaluator = policyEvaluator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public void afterOpened(Long requestId, BillingOperationType type, Object context) {
        if (type != BillingOperationType.CHANGE_PRICE) {
            return;
        }
        ChangePriceCommand command = (ChangePriceCommand) context;
        Price price = priceLocker.lockForUpdate(command.priceId());
        policyEvaluator.requireNoOpenProposal(new PriceProposalSlot(price.getId(), price.findOpenProposal().isPresent()));
        PriceVersion proposal = price.proposePriceVersion(command.baseAmount(), command.marketingDiscount(), command.effectiveFrom(), command.changeReason(),
                command.requestedByPersonId(), requestId, false, LocalDate.now(clock), Instant.now(clock));
        versionRepository.saveAndFlush(proposal);
        log.info("Price proposal PROPOSED: price={}, feeType={}, version={}, effectiveFrom={}, authorizationRequestId={}", price.getId(), price.getFeeType(),
                proposal.getId(), proposal.getEffectiveFrom(), requestId);
    }

    @Override
    public void afterRejected(Long requestId, BillingOperationType type, Long deciderId, String reason) {
        findOpenProposal(requestId, type).ifPresent(version -> {
            policyEvaluator.requireRejectionReason(new PriceRejectionCheck(reason));
            reject(version, deciderId, reason);
        });
    }

    @Override
    public void afterResumeRejected(Long requestId, BillingOperationType type, Long deciderId, String reason) {
        findOpenProposal(requestId, type).ifPresent(version -> reject(version, deciderId, REJECTED_ON_APPROVAL + reason));
    }

    @Override
    public void afterWithdrawn(Long requestId, BillingOperationType type, Long requesterId) {
        findOpenProposal(requestId, type).ifPresent(version -> {
            Price price = priceLocker.lockForUpdate(version.getPrice().getId());
            price.withdrawProposedVersion(version, requesterId, Instant.now(clock));
            versionRepository.save(version);
            log.info("Price proposal WITHDRAWN: price={}, version={}", price.getId(), version.getId());
        });
    }

    @Override
    public void afterExpired(Long requestId, BillingOperationType type) {
        findOpenProposal(requestId, type).ifPresent(version -> {
            Price price = priceLocker.lockForUpdate(version.getPrice().getId());
            price.expireProposedVersion(version, Instant.now(clock));
            versionRepository.save(version);
            log.warn("Price proposal EXPIRED without decision: price={}, version={}, effectiveFrom={}", price.getId(), version.getId(), version.getEffectiveFrom());
        });
    }

    private void reject(PriceVersion version, Long deciderId, String reason) {
        Price price = priceLocker.lockForUpdate(version.getPrice().getId());
        price.rejectProposedVersion(version, deciderId, reason, Instant.now(clock));
        versionRepository.save(version);
        eventPublisher.publishEvent(PriceChangeDecidedEvent.rejectedFor(version.getId(), price.getId(), price.getDescription(), version.getEffectiveFrom(),
                version.getProposedById(), reason));
        log.info("Price proposal REJECTED: price={}, version={}", price.getId(), version.getId());
    }

    private Optional<PriceVersion> findOpenProposal(Long requestId, BillingOperationType type) {
        if (type != BillingOperationType.CHANGE_PRICE) {
            return Optional.empty();
        }
        return versionRepository.findFirstByAuthorizationRequestId(requestId).filter(PriceVersion::isOpenProposal);
    }
}
