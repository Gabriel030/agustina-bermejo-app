package com.red_escuela.backend.platform.specialist;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EnvironmentWhatsAppProviderConfiguration implements WhatsAppProviderConfiguration {

    private static final String NONE = "none";

    private final String provider;

    public EnvironmentWhatsAppProviderConfiguration(@Value("${app.broadcast.providers.whatsapp.provider:none}") String provider) {
        this.provider = provider == null || provider.isBlank() ? NONE : provider.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String name() {
        return provider;
    }
}
