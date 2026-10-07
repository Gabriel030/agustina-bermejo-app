package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.exception.ProposalAlreadyOpenException;
import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.billing.configuration.policy.ProposalSlot;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.events.ConfigurationChangeDecidedEvent;
import com.red_escuela.backend.billing.operation.AuthorizationRequestObserver;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class ConfigurationProposalObserver implements AuthorizationRequestObserver {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationProposalObserver.class);
    private static final String REJECTED_ON_APPROVAL = "Rechazada al aprobar: ";

    private final ConfigurationVersionRepository repository;
    private final ConfigurationPolicyEvaluator policyEvaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ConfigurationProposalObserver(ConfigurationVersionRepository repository, ConfigurationPolicyEvaluator policyEvaluator,
                                         ApplicationEventPublisher eventPublisher, Clock clock) {
        this.repository = repository;
        this.policyEvaluator = policyEvaluator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public void afterOpened(Long requestId, BillingOperationType type, Object context) {
        if (type != BillingOperationType.CHANGE_BILLING_CONFIGURATION) {
            return;
        }
        ChangeBillingConfigurationCommand command = (ChangeBillingConfigurationCommand) context;
        String topicKey = command.topic().key();
        policyEvaluator.requireNoOpenProposal(new ProposalSlot(topicKey,
                repository.existsByScopeAndTopicKeyAndStatus(command.topic().scope(), topicKey, ConfigurationVersionStatus.PROPOSED)));
        Long previousId = repository.findEffectiveVersion(command.topic().scope().name(), topicKey, LocalDate.now(clock)).map(ConfigurationVersion::getId).orElse(null);
        try {
            ConfigurationVersion proposal = repository.saveAndFlush(ConfigurationVersion.propose(command.topic(), command.values(), command.effectiveFrom(),
                    command.changeReason(), command.requestedByPersonId(), requestId, previousId, Instant.now(clock)));
            log.info("Configuration proposal PROPOSED: scope={}, topic={}, version={}, effectiveFrom={}, authorizationRequestId={}", proposal.getScope(),
                    topicKey, proposal.getId(), proposal.getEffectiveFrom(), requestId);
        } catch (DataIntegrityViolationException duplicated) {
            throw new ProposalAlreadyOpenException(List.of(new PolicyViolation("PROPOSAL_ALREADY_OPEN", "Ya hay una propuesta pendiente sobre este tema.")));
        }
    }

    @Override
    public void afterRejected(Long requestId, BillingOperationType type, Long deciderId, String reason) {
        findOpenProposal(requestId, type).ifPresent(version -> reject(version, deciderId, reason));
    }

    @Override
    public void afterResumeRejected(Long requestId, BillingOperationType type, Long deciderId, String reason) {
        findOpenProposal(requestId, type).ifPresent(version -> reject(version, deciderId, REJECTED_ON_APPROVAL + reason));
    }

    @Override
    public void afterWithdrawn(Long requestId, BillingOperationType type, Long requesterId) {
        findOpenProposal(requestId, type).ifPresent(version -> {
            version.withdraw(requesterId, Instant.now(clock));
            repository.save(version);
            log.info("Configuration proposal WITHDRAWN: scope={}, topic={}, version={}", version.getScope(), version.getTopicKey(), version.getId());
        });
    }

    @Override
    public void afterExpired(Long requestId, BillingOperationType type) {
        findOpenProposal(requestId, type).ifPresent(version -> {
            version.expire(Instant.now(clock));
            repository.save(version);
            log.warn("Configuration proposal EXPIRED without decision: scope={}, topic={}, version={}, effectiveFrom={}", version.getScope(),
                    version.getTopicKey(), version.getId(), version.getEffectiveFrom());
        });
    }

    private void reject(ConfigurationVersion version, Long deciderId, String reason) {
        version.reject(deciderId, reason, Instant.now(clock));
        repository.save(version);
        eventPublisher.publishEvent(ConfigurationChangeDecidedEvent.rejectedFor(version.getId(), version.getScope().name(), version.getTopicKey(),
                version.getProposedById(), reason));
        log.info("Configuration proposal REJECTED: scope={}, topic={}, version={}", version.getScope(), version.getTopicKey(), version.getId());
    }

    private Optional<ConfigurationVersion> findOpenProposal(Long requestId, BillingOperationType type) {
        if (type != BillingOperationType.CHANGE_BILLING_CONFIGURATION) {
            return Optional.empty();
        }
        return repository.findFirstByAuthorizationRequestId(requestId).filter(ConfigurationVersion::isOpenProposal);
    }
}
