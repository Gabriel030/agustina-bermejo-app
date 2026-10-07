package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.policy.CreateLatePaymentSettingsCommand;
import com.red_escuela.backend.billing.configuration.policy.LatePaymentSettingsValues;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.billing.events.LatePaymentSettingsCreatedEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.repository.CollectionsPolicyRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.event.DomainEvent;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class CreateLatePaymentSettingsOperation implements BillingOperation<CreateLatePaymentSettingsCommand, CollectionsPolicy> {
    private final ConfigurationPolicyEvaluator policyEvaluator;
    private final CollectionsPolicyRepository collectionsPolicyRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public CreateLatePaymentSettingsOperation(ConfigurationPolicyEvaluator policyEvaluator, CollectionsPolicyRepository collectionsPolicyRepository,
                                              EntityManager entityManager, Clock clock) {
        this.policyEvaluator = policyEvaluator;
        this.collectionsPolicyRepository = collectionsPolicyRepository;
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
    public CollectionsPolicy processOperation(CreateLatePaymentSettingsCommand command) {
        return collectionsPolicyRepository.save(CollectionsPolicy.builder()
                .gracePeriodDays(command.gracePeriodDays())
                .surchargeRate(command.surchargeRate())
                .suspensionAfterDays(command.suspensionAfterDays())
                .suspensionAfterInvoiceCount(command.suspensionAfterInvoiceCount())
                .effectiveFrom(effectiveFromOf(command, LocalDate.now(clock)))
                .createdBy(entityManager.getReference(Person.class, command.requestedByPersonId()))
                .build());
    }

    @Override
    public List<LatePaymentSettingsCreatedEvent> domainEventsOf(CreateLatePaymentSettingsCommand command, CollectionsPolicy policy) {
        return List.of(LatePaymentSettingsCreatedEvent.createdFor(policy.getId(), policy.getEffectiveFrom(), command.requestedByPersonId()));
    }

    private LocalDate effectiveFromOf(CreateLatePaymentSettingsCommand command, LocalDate today) {
        return Optional.ofNullable(command.effectiveFrom()).orElse(today);
    }
}
