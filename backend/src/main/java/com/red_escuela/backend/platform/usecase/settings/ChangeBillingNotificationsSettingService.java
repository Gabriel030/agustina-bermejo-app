package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.InstituteSettingChange;
import com.red_escuela.backend.platform.domain.InstituteSettings.BillingNotificationsSetting;
import com.red_escuela.backend.platform.model.InstituteSettingCode;
import com.red_escuela.backend.platform.service.InstituteSettingChanger;
import com.red_escuela.backend.platform.service.InstituteSettingsReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ChangeBillingNotificationsSettingService implements ChangeBillingNotificationsSettingUseCase {

    private final InstituteSettingChanger settingChanger;
    private final InstituteSettingsReader settingsReader;

    public ChangeBillingNotificationsSettingService(InstituteSettingChanger settingChanger, InstituteSettingsReader settingsReader) {
        this.settingChanger = settingChanger;
        this.settingsReader = settingsReader;
    }

    @Override
    public BillingNotificationsSetting change(boolean enabled, Long expectedVersion, String reason) {
        settingChanger.change(new InstituteSettingChange(InstituteSettingCode.BILLING_NOTIFICATIONS, enabled, expectedVersion, reason));
        return settingsReader.readBillingNotifications();
    }
}
