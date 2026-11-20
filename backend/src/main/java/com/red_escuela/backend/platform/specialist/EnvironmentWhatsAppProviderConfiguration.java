package com.red_escuela.backend.platform.specialist;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EnvironmentWhatsAppProviderConfiguration implements WhatsAppProviderConfiguration {

    private static final String NONE = "none";

    private static final String META = "meta";

    private final String provider;
    private final boolean metaCredentialsComplete;

    public EnvironmentWhatsAppProviderConfiguration(
            @Value("${app.broadcast.providers.whatsapp.provider:none}") String provider,
            @Value("${app.broadcast.providers.whatsapp.meta.phone-number-id:}") String phoneNumberId,
            @Value("${app.broadcast.providers.whatsapp.meta.business-account-id:}") String businessAccountId,
            @Value("${app.broadcast.providers.whatsapp.meta.access-token:}") String accessToken) {
        this.provider = provider == null || provider.isBlank() ? NONE : provider.strip().toLowerCase(Locale.ROOT);
        this.metaCredentialsComplete = !isBlank(phoneNumberId) && !isBlank(businessAccountId) && !isBlank(accessToken);
    }

    @Override
    public boolean isConfigured() {
        return META.equals(provider) && metaCredentialsComplete;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String name() {
        return provider;
    }
}
