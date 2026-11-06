package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.InstituteSettingChange;
import com.red_escuela.backend.platform.domain.InstituteSettings.PaymentMethodSetting;
import com.red_escuela.backend.platform.model.PaymentMethodCode;
import com.red_escuela.backend.platform.service.InstituteSettingChanger;
import com.red_escuela.backend.platform.service.InstituteSettingsReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ChangePaymentMethodSettingService implements ChangePaymentMethodSettingUseCase {

    private final InstituteSettingChanger settingChanger;
    private final InstituteSettingsReader settingsReader;

    public ChangePaymentMethodSettingService(InstituteSettingChanger settingChanger, InstituteSettingsReader settingsReader) {
        this.settingChanger = settingChanger;
        this.settingsReader = settingsReader;
    }

    @Override
    public PaymentMethodSetting change(PaymentMethodCode code, boolean enabled, Long expectedVersion, String reason) {
        settingChanger.change(new InstituteSettingChange(code.settingCode(), enabled, expectedVersion, reason));
        return settingsReader.readPaymentMethod(code);
    }
}
