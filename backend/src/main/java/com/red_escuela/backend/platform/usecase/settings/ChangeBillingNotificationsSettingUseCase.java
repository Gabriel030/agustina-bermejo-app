package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.InstituteSettings.BillingNotificationsSetting;

public interface ChangeBillingNotificationsSettingUseCase {
    BillingNotificationsSetting change(boolean enabled, Long expectedVersion, String reason);
}
