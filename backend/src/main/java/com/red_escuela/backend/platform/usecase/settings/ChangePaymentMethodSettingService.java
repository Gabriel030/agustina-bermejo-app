package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.InstituteSettings.PaymentMethodSetting;
import com.red_escuela.backend.platform.domain.PaymentMethodSettingChange;
import com.red_escuela.backend.platform.specialist.InstituteSettingsUpdater;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ChangePaymentMethodSettingService implements ChangePaymentMethodSettingUseCase {

    private final InstituteSettingsUpdater settingsUpdater;

    public ChangePaymentMethodSettingService(InstituteSettingsUpdater settingsUpdater) {
        this.settingsUpdater = settingsUpdater;
    }

    @Override
    public PaymentMethodSetting change(PaymentMethodSettingChange change) {
        return settingsUpdater.changePaymentMethod(change);
    }
}
