package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.BillingNotificationsSettingChange;
import com.red_escuela.backend.platform.domain.InstituteSettings.BillingNotificationsSetting;
import com.red_escuela.backend.platform.specialist.InstituteSettingsUpdater;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ChangeBillingNotificationsSettingService implements ChangeBillingNotificationsSettingUseCase {

    private final InstituteSettingsUpdater settingsUpdater;

    public ChangeBillingNotificationsSettingService(InstituteSettingsUpdater settingsUpdater) {
        this.settingsUpdater = settingsUpdater;
    }

    @Override
    public BillingNotificationsSetting change(BillingNotificationsSettingChange change) {
        return settingsUpdater.changeBillingNotifications(change);
    }
}
