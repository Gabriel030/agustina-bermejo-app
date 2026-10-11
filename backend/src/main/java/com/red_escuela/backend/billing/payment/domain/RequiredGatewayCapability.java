package com.red_escuela.backend.billing.payment.domain;

public enum RequiredGatewayCapability {
    ONLINE_CHECKOUT,
    FULL_REFUND,
    PARTIAL_REFUND,
    CHARGEBACK_NOTIFICATION,
    CARD_INSTALLMENTS,
    AUTOMATIC_DEBIT,
    MULTI_ITEM_CHECKOUT,
    AUTHORIZATION_HOLD
}
