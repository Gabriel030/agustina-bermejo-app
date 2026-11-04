package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import java.util.Map;

public record UpdateNotificationPreferencesRequest(Map<String, Map<String, Boolean>> preferences) implements SelfValidatingRequest {
    private static final int MAX_ENTRIES = 50;
    private static final int MAX_KEY_LENGTH = 100;

    public UpdateNotificationPreferencesRequest {
        RequestLimits.requireMaxSize("preferences", preferences, MAX_ENTRIES);
        if (preferences != null) {
            preferences.forEach((key, channels) -> {
                RequestLimits.requireMaxLength("preferences", key, MAX_KEY_LENGTH);
                RequestLimits.requireMaxSize("preferences", channels, MAX_ENTRIES);
                if (channels != null) {
                    channels.keySet().forEach(channel -> RequestLimits.requireMaxLength("preferences", channel, MAX_KEY_LENGTH));
                }
            });
        }
    }
}
