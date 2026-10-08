package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.exception.ConfigurationVersionNotFoundException;
import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.billing.configuration.policy.ConfigurationChangeCheck;
import com.red_escuela.backend.billing.configuration.policy.ProposalApprovalCheck;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.events.ConfigurationChangeDecidedEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class ChangeBillingConfigurationOperation implements BillingOperation<ChangeBillingConfigurationCommand, ConfigurationVersion> {
    private static final Logger log = LoggerFactory.getLogger(ChangeBillingConfigurationOperation.class);

    private final ConfigurationPolicyEvaluator policyEvaluator;
    private final ConfigurationScopeHandlers scopeHandlers;
    private final ConfigurationVersionRepository repository;
    private final ConfigurationVersionActivator activator;
    private final BillingActorResolver actorResolver;
    private final Clock clock;

    public ChangeBillingConfigurationOperation(ConfigurationPolicyEvaluator policyEvaluator, ConfigurationScopeHandlers scopeHandlers,
                                               ConfigurationVersionRepository repository, ConfigurationVersionActivator activator,
                                               BillingActorResolver actorResolver, Clock clock) {
        this.policyEvaluator = policyEvaluator;
        this.scopeHandlers = scopeHandlers;
        this.repository = repository;
        this.activator = activator;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CHANGE_BILLING_CONFIGURATION;
    }

    @Override
    public Class<ChangeBillingConfigurationCommand> contextType() {
        return ChangeBillingConfigurationCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        List<String> moments = new ArrayList<>(List.of("BILLING_CONFIGURATION_CHANGE", "BILLING_CONFIGURATION_PROPOSAL", "CONFIGURATION_PROPOSAL_APPROVAL"));
        for (ConfigurationScope scope : ConfigurationScope.values()) {
            moments.addAll(scopeHandlers.handlerOf(scope).validationMoments());
        }
        return moments.stream().distinct().toList();
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(ConfigurationChangeDecidedEvent.class);
    }

    @Override
    public void requireValidContext(ChangeBillingConfigurationCommand command) {
        policyEvaluator.requireValidChange(new ConfigurationChangeCheck(command.topic().scope(), command.topic().key(), command.effectiveFrom(),
                LocalDate.now(clock), command.changeReason()));
        scopeHandlers.handlerOf(command.topic().scope()).requireCoherentValues(command.topic(), command.values());
    }

    @Override
    public ConfigurationVersion processOperation(ChangeBillingConfigurationCommand command) {
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        ConfigurationVersion version = command.requestedByDirector() ? publishDirectly(command, today, now) : approveProposal(command, today, now);
        ConfigurationVersion saved = repository.saveAndFlush(version);
        if (saved.getStatus() == ConfigurationVersionStatus.EFFECTIVE) {
            activator.supersedeOlderEffectiveVersions(saved);
        }
        log.info("Configuration version {}: scope={}, topic={}, version={}, effectiveFrom={}, directPublication={}", saved.getStatus(), saved.getScope(),
                saved.getTopicKey(), saved.getId(), saved.getEffectiveFrom(), saved.isDirectPublication());
        return saved;
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(ChangeBillingConfigurationCommand command, ConfigurationVersion version) {
        if (command.requestedByDirector()) {
            return List.of();
        }
        return List.of(ConfigurationChangeDecidedEvent.approvedFor(version.getId(), version.getScope().name(), version.getTopicKey(), version.getProposedById()));
    }

    private ConfigurationVersion publishDirectly(ChangeBillingConfigurationCommand command, LocalDate today, Instant now) {
        Long previousId = repository.findEffectiveVersion(command.topic().scope().name(), command.topic().key(), today).map(ConfigurationVersion::getId).orElse(null);
        return ConfigurationVersion.publish(command.topic(), command.values(), command.effectiveFrom(), command.changeReason(),
                command.requestedByPersonId(), today, previousId, now);
    }

    private ConfigurationVersion approveProposal(ChangeBillingConfigurationCommand command, LocalDate today, Instant now) {
        ConfigurationVersion proposal = repository.findFirstByScopeAndTopicKeyAndStatus(command.topic().scope(), command.topic().key(), ConfigurationVersionStatus.PROPOSED)
                .orElseThrow(() -> new ConfigurationVersionNotFoundException(null));
        policyEvaluator.requireProposalNotExpired(new ProposalApprovalCheck(proposal.getEffectiveFrom(), today, proposal.getProposedAt().atZone(clock.getZone()).toLocalDate()));
        proposal.approve(actorResolver.currentPersonId(), null, today, now);
        return proposal;
    }
}
