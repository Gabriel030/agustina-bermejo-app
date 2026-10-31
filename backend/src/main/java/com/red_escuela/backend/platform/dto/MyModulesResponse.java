package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.EffectiveModules;
import com.red_escuela.backend.platform.model.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;

import java.util.List;

public record MyModulesResponse(
        BillingModule billing,
        EnabledFlag fiscalInvoicing,
        EnabledFlag analytics,
        List<PaymentMethodAvailability> paymentMethods,
        EnabledFlag billingNotifications,
        EnabledFlag externalBilling) {

    public record BillingModule(BillingMode mode) {
    }

    public record EnabledFlag(boolean enabled) {
    }

    public record PaymentMethodAvailability(String code, boolean available) {
    }

    public static MyModulesResponse from(EffectiveModules modules) {
        return new MyModulesResponse(
                new BillingModule(modules.billingMode()),
                new EnabledFlag(modules.availableWithBilling(FeatureCode.FISCAL_INVOICING)),
                new EnabledFlag(modules.analyticsEnabled()),
                List.of(
                        new PaymentMethodAvailability("CASH", modules.availableWithBilling(FeatureCode.PAYMENT_METHOD_CASH)),
                        new PaymentMethodAvailability("BANK_TRANSFER", modules.availableWithBilling(FeatureCode.PAYMENT_METHOD_BANK_TRANSFER)),
                        new PaymentMethodAvailability("GATEWAY", modules.availableWithBilling(FeatureCode.PAYMENT_METHOD_GATEWAY))),
                new EnabledFlag(modules.availableWithBilling(FeatureCode.BILLING_NOTIFICATIONS)),
                new EnabledFlag(modules.availableWithBilling(FeatureCode.EXTERNAL_BILLING)));
    }
}
