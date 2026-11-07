package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.model.PaymentMethodCode;

import java.util.List;

public record InstituteSettings(List<PaymentMethodSetting> paymentMethods, BillingNotificationsSetting billingNotifications) {

    public record PaymentMethodSetting(PaymentMethodCode code, boolean platformEnabled, boolean enabled, boolean effective, long version) {
    }

    public record BillingNotificationsSetting(boolean platformEnabled, boolean enabled, boolean effective, long version) {
    }
}
