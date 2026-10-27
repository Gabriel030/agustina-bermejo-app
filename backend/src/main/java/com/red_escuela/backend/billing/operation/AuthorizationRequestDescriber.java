package com.red_escuela.backend.billing.operation;

public interface AuthorizationRequestDescriber<C> {
    BillingOperationType type();

    AuthorizationRequestDescription describe(C context, Long authorizationRequestId);
}
