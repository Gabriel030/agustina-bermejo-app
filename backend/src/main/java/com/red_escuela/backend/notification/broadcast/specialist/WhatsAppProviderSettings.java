package com.red_escuela.backend.notification.broadcast.specialist;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppProviderSettings {

    private final int dailyLimit;

    public WhatsAppProviderSettings(@Value("${app.broadcast.providers.whatsapp.meta.daily-limit}") int dailyLimit) {
        if (dailyLimit < 0) {
            throw new IllegalArgumentException("The WhatsApp daily limit cannot be negative");
        }
        this.dailyLimit = dailyLimit;
    }

    public int dailyLimit() {
        return dailyLimit;
    }
}
