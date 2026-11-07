package com.red_escuela.backend.platform.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("model")
public enum PaymentMethodCode {
    CASH(InstituteSettingCode.PAYMENT_METHOD_CASH, false),
    BANK_TRANSFER(InstituteSettingCode.PAYMENT_METHOD_BANK_TRANSFER, true),
    GATEWAY(InstituteSettingCode.PAYMENT_METHOD_GATEWAY, true);

    private final InstituteSettingCode settingCode;
    private final boolean digital;

    PaymentMethodCode(InstituteSettingCode settingCode, boolean digital) {
        this.settingCode = settingCode;
        this.digital = digital;
    }

    public InstituteSettingCode settingCode() {
        return settingCode;
    }

    public FeatureCode featureCode() {
        return settingCode.platformFeature();
    }

    public boolean isDigital() {
        return digital;
    }
}
