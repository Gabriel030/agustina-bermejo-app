package com.red_escuela.backend.billing.pricing.domain;

public enum PriceVersionEvent {
    PROPOSE,
    PUBLISH_FOR_FUTURE,
    PUBLISH_FOR_TODAY,
    APPROVE_FOR_FUTURE,
    APPROVE_FOR_TODAY,
    REJECT,
    WITHDRAW,
    EXPIRE,
    REACH_START,
    CANCEL,
    SUPERSEDE
}
