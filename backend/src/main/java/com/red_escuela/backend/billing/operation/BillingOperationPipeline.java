package com.red_escuela.backend.billing.operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

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
        for (PreProcessingHandler<C> handler : handlerRegistry.<C>preHandlersFor(type)) {
            PreProcessingResult result = handler.handleBeforeProcessing(type, context, priorAuthorization);
            if (result instanceof PreProcessingResult.RequiresAuthorization required) {
                Long requestId = authorizationRequests.openAuthorizationRequest(type, context, required.requiredRole(), required.reason(), required.expiresOn());
                log.info("Operation awaiting authorization: type={}, authorizationRequestId={}", type, requestId);
                return new BillingOperationOutcome.AwaitingAuthorization<>(requestId);
            }
        }
        R result = operation.processOperation(context);
        operation.domainEventsOf(context, result).forEach(eventPublisher::publishEvent);
        log.info("Operation processed: type={}", type);
        return new BillingOperationOutcome.Processed<>(result);
    }
}
