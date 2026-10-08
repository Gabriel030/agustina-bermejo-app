package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.exception.ConfigurationVersionNotFoundException;
import com.red_escuela.backend.billing.configuration.policy.CancelConfigurationVersionCommand;
import com.red_escuela.backend.billing.configuration.policy.VersionCancellationCheck;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.events.ConfigurationVersionCancelledEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class CancelConfigurationVersionOperation implements BillingOperation<CancelConfigurationVersionCommand, ConfigurationVersion> {
    private static final Logger log = LoggerFactory.getLogger(CancelConfigurationVersionOperation.class);

    private final ConfigurationPolicyEvaluator policyEvaluator;
    private final ConfigurationVersionRepository repository;
    private final Clock clock;

    public CancelConfigurationVersionOperation(ConfigurationPolicyEvaluator policyEvaluator, ConfigurationVersionRepository repository, Clock clock) {
        this.policyEvaluator = policyEvaluator;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CANCEL_CONFIGURATION_VERSION;
    }

    @Override
    public Class<CancelConfigurationVersionCommand> contextType() {
        return CancelConfigurationVersionCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of("CONFIGURATION_VERSION_CANCELLATION");
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(ConfigurationVersionCancelledEvent.class);
    }

    @Override
    public void requireValidContext(CancelConfigurationVersionCommand command) {
        ConfigurationVersion version = findVersion(command);
        policyEvaluator.requireCancellableVersion(new VersionCancellationCheck(version.getStatus(), version.getEffectiveFrom(), LocalDate.now(clock), command.reason()));
    }

    @Override
    public ConfigurationVersion processOperation(CancelConfigurationVersionCommand command) {
        ConfigurationVersion version = findVersion(command);
        version.cancel(command.cancelledByPersonId(), command.reason(), Instant.now(clock));
        log.info("Configuration version CANCELLED: scope={}, topic={}, version={}, effectiveFrom={}", version.getScope(), version.getTopicKey(), version.getId(), version.getEffectiveFrom());
        return repository.saveAndFlush(version);
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(CancelConfigurationVersionCommand command, ConfigurationVersion version) {
        return List.of(ConfigurationVersionCancelledEvent.cancelledFor(version.getId(), version.getScope().name(), version.getTopicKey(),
                version.getProposedById(), version.getEffectiveFrom(), command.reason()));
    }

    private ConfigurationVersion findVersion(CancelConfigurationVersionCommand command) {
        return repository.findById(command.configurationVersionId()).orElseThrow(() -> new ConfigurationVersionNotFoundException(command.configurationVersionId()));
    }
}
