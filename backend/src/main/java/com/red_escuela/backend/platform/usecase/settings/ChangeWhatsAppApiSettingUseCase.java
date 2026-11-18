package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.WhatsAppApiSetting;
import com.red_escuela.backend.platform.domain.WhatsAppApiSettingChange;

public interface ChangeWhatsAppApiSettingUseCase {
    WhatsAppApiSetting change(WhatsAppApiSettingChange change);
}
