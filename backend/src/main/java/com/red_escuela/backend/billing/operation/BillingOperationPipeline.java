package com.red_escuela.backend.billing.operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class BillingOperationPipeline {
    private static final Logger log = LoggerFactory.getLogger(BillingOperationPipeline.class);

    private final OperationHandlerRegistry handlerRegistry;
    private final AuthorizationRequestPort authorizationRequests;
    private final ApplicationEventPublisher eventPublisher;

    public BillingOperationPipeline(OperationHandlerRegistry handlerRegistry, AuthorizationRequestPort authorizationRequests,
                                    ApplicationEventPublisher eventPublisher) {
        this.handlerRegistry = handlerRegistry;
        this.authorizationRequests = authorizationRequests;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public <C, R> BillingOperationOutcome<R> runOperation(BillingOperation<C, R> operation, C context) {
        return runStages(operation, context, PriorAuthorization.ABSENT);
    }

    @Transactional
    public <C, R> BillingOperationOutcome<R> resumeAuthorizedOperation(BillingOperation<C, R> operation, C context) {
        return runStages(operation, context, PriorAuthorization.APPROVED);
    }

    private <C, R> BillingOperationOutcome<R> runStages(BillingOperation<C, R> operation, C context, PriorAuthorization priorAuthorization) {
        BillingOperationType type = operation.type();
        operation.requireValidContext(context);
        List<PreProcessingHandler<C>> handlers = handlerRegistry.<C>preHandlersFor(type);
        for (int index = 0; index < handlers.size(); index++) {
            PreProcessingHandler<C> handler = handlers.get(index);
            PreProcessingResult result = handler.handleBeforeProcessing(type, context, priorAuthorization);
            if (result instanceof PreProcessingResult.RequiresAuthorization required) {
                Long requestId = authorizationRequests.openAuthorizationRequest(type, context, required.requiredRole(), required.reason(), required.expiresOn());
                log.info("Operation awaiting authorization: type={}, authorizationRequestId={}", type, requestId);
                handlers.subList(0, index + 1).forEach(awaiting -> awaiting.handleAwaitingAuthorization(type, context, requestId));
                return new BillingOperationOutcome.AwaitingAuthorization<>(requestId);
            }
            if (result instanceof PreProcessingResult.ReplayStoredOutcome replay) {
                log.info("Operation replayed from its stored outcome: type={}", type);
                @SuppressWarnings("unchecked")
                BillingOperationOutcome<R> stored = (BillingOperationOutcome<R>) replay.storedOutcome();
                return stored;
            }
        }
        R result = operation.processOperation(context);
        handlers.forEach(handler -> handler.handleAfterProcessing(type, context, result));
        operation.domainEventsOf(context, result).forEach(eventPublisher::publishEvent);
        log.info("Operation processed: type={}", type);
        return new BillingOperationOutcome.Processed<>(result);
    }
}
