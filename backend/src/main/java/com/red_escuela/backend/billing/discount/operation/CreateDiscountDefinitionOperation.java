package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.policy.DiscountDefinitionCheck;
import com.red_escuela.backend.billing.discount.policy.DiscountDefinitionRejections;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.service.DiscountOfferingScopeResolver;
import com.red_escuela.backend.billing.events.DiscountDefinitionCreatedEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class CreateDiscountDefinitionOperation implements BillingOperation<CreateDiscountDefinitionCommand, DiscountDefinition> {
    private static final Logger log = LoggerFactory.getLogger(CreateDiscountDefinitionOperation.class);

    private final PolicySet<DiscountDefinitionCheck> definitionPolicies;
    private final DiscountOfferingScopeResolver scopeResolver;
    private final DiscountDefinitionRepository repository;
    private final Clock clock;

    public CreateDiscountDefinitionOperation(PolicySet<DiscountDefinitionCheck> definitionPolicies, DiscountOfferingScopeResolver scopeResolver,
                                             DiscountDefinitionRepository repository, Clock clock) {
        this.definitionPolicies = definitionPolicies;
        this.scopeResolver = scopeResolver;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CREATE_DISCOUNT_DEFINITION;
    }

    @Override
    public Class<CreateDiscountDefinitionCommand> contextType() {
        return CreateDiscountDefinitionCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(definitionPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(DiscountDefinitionCreatedEvent.class);
    }

    @Override
    public void requireValidContext(CreateDiscountDefinitionCommand command) {
        definitionPolicies.evaluateAllRules(scopeResolver.resolveScopeOf(command.draft())).throwIfDenied(DiscountDefinitionRejections::rejectionOf);
    }

    @Override
    public DiscountDefinition processOperation(CreateDiscountDefinitionCommand command) {
        Instant now = Instant.now(clock);
        DiscountDefinition definition = command.requestedByDirector()
                ? DiscountDefinition.publishDiscountDefinition(command.draft(), command.requestedByPersonId(), now)
                : DiscountDefinition.createApprovedDiscountDefinition(command.draft(), command.requestedByPersonId(), command.authorizationRequestId(), now);
        DiscountDefinition saved = repository.saveAndFlush(definition);
        log.info("Promoción creada: id={}, tipo={}, tipoDeFactura={}, calculo={}, cuotas={}, prioridad={}, actor={}, publicacionDirecta={}, authorizationRequestId={}",
                saved.getId(), saved.getDiscountType(), saved.getApplicableInvoiceType(), saved.getCalculation(), saved.getInstallmentsTotal(), saved.getPriority(),
                saved.getCreatedById(), saved.isDirectPublication(), saved.getAuthorizationRequestId());
        return saved;
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(CreateDiscountDefinitionCommand command, DiscountDefinition definition) {
        return List.of(DiscountDefinitionCreatedEvent.createdFor(definition.getId(), definition.getDiscountType().name(), definition.getCreatedById(),
                definition.isDirectPublication()));
    }
}
