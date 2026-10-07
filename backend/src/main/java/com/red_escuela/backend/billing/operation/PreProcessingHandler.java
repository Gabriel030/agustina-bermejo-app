package com.red_escuela.backend.billing.operation;

public interface PreProcessingHandler<C> {
    PreProcessingResult handleBeforeProcessing(BillingOperationType type, C context, PriorAuthorization priorAuthorization);
}
