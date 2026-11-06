package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.BillingNotificationsSettingChange;
import com.red_escuela.backend.platform.domain.InstituteSettings.BillingNotificationsSetting;

public interface ChangeBillingNotificationsSettingUseCase {
    BillingNotificationsSetting change(BillingNotificationsSettingChange change);
}
