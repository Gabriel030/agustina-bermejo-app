package com.red_escuela.backend.billing.authorization.domain;

public enum AuthorizationRequestEvent {
    OPEN,
    APPROVE,
    REJECT,
    WITHDRAW,
    EXPIRE
}
