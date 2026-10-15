package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStateMachine;
import com.red_escuela.backend.billing.discount.exception.DiscountDefinitionNotFoundException;
import com.red_escuela.backend.billing.discount.policy.DiscountClosureCheck;
import com.red_escuela.backend.billing.discount.policy.DiscountDefinitionRejections;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.service.DiscountActorResolver;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class CloseDiscountDefinitionOperation implements BillingOperation<CloseDiscountDefinitionCommand, DiscountDefinition> {
    private static final Logger log = LoggerFactory.getLogger(CloseDiscountDefinitionOperation.class);

    private final PolicySet<DiscountClosureCheck> closurePolicies;
    private final DiscountDefinitionRepository repository;
    private final DiscountActorResolver actorResolver;
    private final Clock clock;

    public CloseDiscountDefinitionOperation(PolicySet<DiscountClosureCheck> closurePolicies, DiscountDefinitionRepository repository,
                                            DiscountActorResolver actorResolver, Clock clock) {
        this.closurePolicies = closurePolicies;
        this.repository = repository;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CLOSE_DISCOUNT_DEFINITION;
    }

    @Override
    public Class<CloseDiscountDefinitionCommand> contextType() {
        return CloseDiscountDefinitionCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(closurePolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of();
    }

    @Override
    public void requireValidContext(CloseDiscountDefinitionCommand command) {
        closurePolicies.evaluateAllRules(new DiscountClosureCheck(command.reason())).throwIfDenied(DiscountDefinitionRejections::rejectionOf);
        DiscountDefinitionStateMachine.requireClosableFrom(repository.findStatusById(command.discountDefinitionId())
                .orElseThrow(() -> new DiscountDefinitionNotFoundException(command.discountDefinitionId())));
    }

    @Override
    public DiscountDefinition processOperation(CloseDiscountDefinitionCommand command) {
        DiscountDefinition definition = repository.findByIdForUpdate(command.discountDefinitionId())
                .orElseThrow(() -> new DiscountDefinitionNotFoundException(command.discountDefinitionId()));
        StatusChangeActor actor = StatusChangeActor.ofPerson(actorResolver.currentPersonId());
        definition.closeDiscountDefinition(command.reason().strip(), actor, Instant.now(clock));
        DiscountDefinition saved = repository.saveAndFlush(definition);
        log.info("Promoción cerrada: id={}, tipo={}, actor={}, solicitante={}", saved.getId(), saved.getDiscountType(), actor.personId(), command.requestedByPersonId());
        return saved;
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(CloseDiscountDefinitionCommand command, DiscountDefinition definition) {
        return List.of();
    }
}
