package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.policy.CreateLatePaymentSettingsCommand;
import com.red_escuela.backend.billing.configuration.policy.LatePaymentSettingsValues;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.events.LatePaymentSettingsCreatedEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class CreateLatePaymentSettingsOperation implements BillingOperation<CreateLatePaymentSettingsCommand, ConfigurationVersion> {
    private static final String LATE_PAYMENT_CHANGE_REASON = "Cambio de la política de mora";

    private final ConfigurationPolicyEvaluator policyEvaluator;
    private final ConfigurationVersionRepository repository;
    private final DirectConfigurationPublisher publisher;
    private final Clock clock;

    public CreateLatePaymentSettingsOperation(ConfigurationPolicyEvaluator policyEvaluator, ConfigurationVersionRepository repository,
                                              DirectConfigurationPublisher publisher, Clock clock) {
        this.policyEvaluator = policyEvaluator;
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS;
    }

    @Override
    public Class<CreateLatePaymentSettingsCommand> contextType() {
        return CreateLatePaymentSettingsCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of("LATE_PAYMENT_SETTINGS");
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(LatePaymentSettingsCreatedEvent.class);
    }

    @Override
    public void requireValidContext(CreateLatePaymentSettingsCommand command) {
        LocalDate today = LocalDate.now(clock);
        policyEvaluator.requireCoherentLatePaymentSettings(new LatePaymentSettingsValues(command.gracePeriodDays(), command.surchargeRate(),
                command.suspensionAfterDays(), command.suspensionAfterInvoiceCount(), effectiveFromOf(command, today), today));
    }

    @Override
    public ConfigurationVersion processOperation(CreateLatePaymentSettingsCommand command) {
        LocalDate effectiveFrom = effectiveFromOf(command, LocalDate.now(clock));
        ConfigurationTopic topic = ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT);
        LatePaymentTerms base = repository.findEffectiveVersion(topic.scope().name(), topic.key(), effectiveFrom)
                .map(version -> (LatePaymentTerms) version.values())
                .orElseGet(LatePaymentTerms::initial);
        LatePaymentTerms terms = new LatePaymentTerms(command.gracePeriodDays(), command.surchargeRate(), base.lateFeeCapMonths(), base.lateFeeCapPercentage(),
                command.suspensionAfterDays(), command.suspensionAfterInvoiceCount(), base.blockNewEnrollmentsWhenOverdue(), base.lateFeeDebitNoteDueDays(),
                base.freezeLateFeesOnWithdrawal());
        return publisher.publishVersion(topic, terms, effectiveFrom, LATE_PAYMENT_CHANGE_REASON, command.requestedByPersonId());
    }

    @Override
    public List<LatePaymentSettingsCreatedEvent> domainEventsOf(CreateLatePaymentSettingsCommand command, ConfigurationVersion policy) {
        return List.of(LatePaymentSettingsCreatedEvent.createdFor(policy.getId(), policy.getEffectiveFrom(), command.requestedByPersonId()));
    }

    private LocalDate effectiveFromOf(CreateLatePaymentSettingsCommand command, LocalDate today) {
        return Optional.ofNullable(command.effectiveFrom()).orElse(today);
    }
}
