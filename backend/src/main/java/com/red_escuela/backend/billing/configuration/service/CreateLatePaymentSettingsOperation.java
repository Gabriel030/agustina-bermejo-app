package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.policy.CreateLatePaymentSettingsCommand;
import com.red_escuela.backend.billing.configuration.policy.LatePaymentSettingsValues;
import com.red_escuela.backend.billing.entity.LatePaymentSettings;
import com.red_escuela.backend.billing.events.LatePaymentSettingsCreatedEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.repository.LatePaymentSettingsRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.event.DomainEvent;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class CreateLatePaymentSettingsOperation implements BillingOperation<CreateLatePaymentSettingsCommand, LatePaymentSettings> {
    private final ConfigurationPolicyEvaluator policyEvaluator;
    private final LatePaymentSettingsRepository latePaymentSettingsRepository;
    private final EntityManager entityManager;
    private final LegacyConfigurationMirror configurationMirror;
    private final Clock clock;

    public CreateLatePaymentSettingsOperation(ConfigurationPolicyEvaluator policyEvaluator, LatePaymentSettingsRepository latePaymentSettingsRepository,
                                              EntityManager entityManager, LegacyConfigurationMirror configurationMirror, Clock clock) {
        this.configurationMirror = configurationMirror;
        this.policyEvaluator = policyEvaluator;
        this.latePaymentSettingsRepository = latePaymentSettingsRepository;
        this.entityManager = entityManager;
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
    public LatePaymentSettings processOperation(CreateLatePaymentSettingsCommand command) {
        LatePaymentSettings saved = latePaymentSettingsRepository.save(LatePaymentSettings.builder()
                .gracePeriodDays(command.gracePeriodDays())
                .surchargeRate(command.surchargeRate())
                .suspensionAfterDays(command.suspensionAfterDays())
                .suspensionAfterInvoiceCount(command.suspensionAfterInvoiceCount())
                .effectiveFrom(effectiveFromOf(command, LocalDate.now(clock)))
                .createdBy(entityManager.getReference(Person.class, command.requestedByPersonId()))
                .build());
        configurationMirror.recordLatePayment(saved, command.requestedByPersonId());
        return saved;
    }

    @Override
    public List<LatePaymentSettingsCreatedEvent> domainEventsOf(CreateLatePaymentSettingsCommand command, LatePaymentSettings policy) {
        return List.of(LatePaymentSettingsCreatedEvent.createdFor(policy.getId(), policy.getEffectiveFrom(), command.requestedByPersonId()));
    }

    private LocalDate effectiveFromOf(CreateLatePaymentSettingsCommand command, LocalDate today) {
        return Optional.ofNullable(command.effectiveFrom()).orElse(today);
    }
}
