package com.red_escuela.backend.billing.operation;

public interface PreProcessingHandler<C> {
    PreProcessingResult handleBeforeProcessing(BillingOperationType type, C context, PriorAuthorization priorAuthorization);

    default void handleAfterProcessing(BillingOperationType type, C context, Object result) {
    }

    default void handleAwaitingAuthorization(BillingOperationType type, C context, Long authorizationRequestId) {
    }
}
