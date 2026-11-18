package com.red_escuela.backend.platform.domain;

import java.util.List;

public record WhatsAppApiSetting(boolean enabled, boolean providerConfigured, int dailyLimit, List<String> pendingRequirements, long version) {
}
