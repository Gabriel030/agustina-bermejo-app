package com.red_escuela.backend.platform.specialist;

import com.red_escuela.backend.platform.domain.BillingNotificationsSettingChange;
import com.red_escuela.backend.platform.domain.InstituteSettingChange;
import com.red_escuela.backend.platform.domain.InstituteSettings.BillingNotificationsSetting;
import com.red_escuela.backend.platform.domain.InstituteSettings.PaymentMethodSetting;
import com.red_escuela.backend.platform.domain.PaymentMethodSettingChange;
import com.red_escuela.backend.platform.model.InstituteSettingCode;
import org.springframework.stereotype.Service;

@Service
public class InstituteSettingsUpdater {

    private final InstituteSettingChanger settingChanger;
    private final InstituteSettingsReader settingsReader;

    public InstituteSettingsUpdater(InstituteSettingChanger settingChanger, InstituteSettingsReader settingsReader) {
        this.settingChanger = settingChanger;
        this.settingsReader = settingsReader;
    }

    public PaymentMethodSetting changePaymentMethod(PaymentMethodSettingChange change) {
        settingChanger.change(new InstituteSettingChange(change.code().settingCode(), change.enabled(), change.expectedVersion(), change.reason()));
        return settingsReader.readPaymentMethod(change.code());
    }

    public BillingNotificationsSetting changeBillingNotifications(BillingNotificationsSettingChange change) {
        settingChanger.change(new InstituteSettingChange(InstituteSettingCode.BILLING_NOTIFICATIONS, change.enabled(), change.expectedVersion(), change.reason()));
        return settingsReader.readBillingNotifications();
    }
}
