package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.InstituteSettings;

import java.util.List;

public record InstituteSettingsResponse(List<PaymentMethodSettingResponse> paymentMethods, BillingNotificationsSettingResponse billingNotifications) {

    public static InstituteSettingsResponse of(InstituteSettings settings) {
        return new InstituteSettingsResponse(settings.paymentMethods().stream().map(PaymentMethodSettingResponse::of).toList(),
                BillingNotificationsSettingResponse.of(settings.billingNotifications()));
    }
}
