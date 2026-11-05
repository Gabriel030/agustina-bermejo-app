package com.red_escuela.backend.platform.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("model")
public enum FeatureCode {
    BILLING,
    FISCAL_INVOICING,
    ANALYTICS,
    PAYMENT_METHOD_CASH,
    PAYMENT_METHOD_BANK_TRANSFER,
    PAYMENT_METHOD_GATEWAY,
    BILLING_NOTIFICATIONS,
    EXTERNAL_BILLING;

    public boolean hasMode() {
        return this == BILLING;
    }
}
