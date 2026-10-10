package com.red_escuela.backend.billing.operation;

public interface ResumedFromAuthorizationRequest<C> {
    C resumedFromAuthorizationRequest(Long authorizationRequestId);
}
