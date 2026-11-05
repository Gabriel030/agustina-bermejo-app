package com.red_escuela.backend.billing.discount.domain;

public enum StudentDiscountEvent {
    DISCOUNT_GRANTED,
    LAST_INSTALLMENT_CONSUMED,
    CONSUMPTION_RETURNED,
    DISCOUNT_REVOKED,
    ENROLLMENT_ENDED
}
