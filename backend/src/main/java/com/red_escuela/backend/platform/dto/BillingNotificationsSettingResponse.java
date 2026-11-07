package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.InstituteSettings.BillingNotificationsSetting;

public record BillingNotificationsSettingResponse(boolean platformEnabled, boolean enabled, boolean effective, long version) {

    public static BillingNotificationsSettingResponse of(BillingNotificationsSetting setting) {
        return new BillingNotificationsSettingResponse(setting.platformEnabled(), setting.enabled(), setting.effective(), setting.version());
    }
}
