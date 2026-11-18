package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.WhatsAppApiSetting;
import com.red_escuela.backend.platform.domain.WhatsAppApiSettingChange;
import com.red_escuela.backend.platform.specialist.WhatsAppApiSettingChanger;
import com.red_escuela.backend.platform.specialist.WhatsAppApiSettingReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ChangeWhatsAppApiSettingService implements ChangeWhatsAppApiSettingUseCase {

    private final WhatsAppApiSettingChanger settingChanger;
    private final WhatsAppApiSettingReader settingReader;

    public ChangeWhatsAppApiSettingService(WhatsAppApiSettingChanger settingChanger, WhatsAppApiSettingReader settingReader) {
        this.settingChanger = settingChanger;
        this.settingReader = settingReader;
    }

    @Override
    public WhatsAppApiSetting change(WhatsAppApiSettingChange change) {
        settingChanger.change(change);
        return settingReader.read();
    }
}
