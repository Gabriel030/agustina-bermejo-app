package com.red_escuela.backend.platform.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("model")
public enum InstituteSettingCode {
    PAYMENT_METHOD_CASH(FeatureCode.PAYMENT_METHOD_CASH),
    PAYMENT_METHOD_BANK_TRANSFER(FeatureCode.PAYMENT_METHOD_BANK_TRANSFER),
    PAYMENT_METHOD_GATEWAY(FeatureCode.PAYMENT_METHOD_GATEWAY),
    BILLING_NOTIFICATIONS(FeatureCode.BILLING_NOTIFICATIONS);

    private final FeatureCode platformFeature;

    InstituteSettingCode(FeatureCode platformFeature) {
        this.platformFeature = platformFeature;
    }

    public FeatureCode platformFeature() {
        return platformFeature;
    }
}
