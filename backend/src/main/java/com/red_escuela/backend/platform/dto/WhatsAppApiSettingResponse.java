package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.WhatsAppApiSetting;

import java.util.List;

public record WhatsAppApiSettingResponse(boolean enabled, boolean providerConfigured, int dailyLimit, List<String> pendingRequirements, long version) {

    public static WhatsAppApiSettingResponse of(WhatsAppApiSetting setting) {
        return new WhatsAppApiSettingResponse(setting.enabled(), setting.providerConfigured(), setting.dailyLimit(), setting.pendingRequirements(), setting.version());
    }
}
