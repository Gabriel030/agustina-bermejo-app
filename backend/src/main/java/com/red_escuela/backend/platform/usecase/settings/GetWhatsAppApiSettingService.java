package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.WhatsAppApiSetting;
import com.red_escuela.backend.platform.specialist.WhatsAppApiSettingReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetWhatsAppApiSettingService implements GetWhatsAppApiSettingUseCase {

    private final WhatsAppApiSettingReader settingReader;

    public GetWhatsAppApiSettingService(WhatsAppApiSettingReader settingReader) {
        this.settingReader = settingReader;
    }

    @Override
    public WhatsAppApiSetting read() {
        return settingReader.read();
    }
}
