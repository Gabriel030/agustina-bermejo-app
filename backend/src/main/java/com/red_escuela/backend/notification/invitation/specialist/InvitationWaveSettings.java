package com.red_escuela.backend.notification.invitation.specialist;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InvitationWaveSettings {

    private final int globalDailyLimit;
    private final int maxRecipients;

    public InvitationWaveSettings(@Value("${app.broadcast.providers.brevo.daily-limit:300}") int globalDailyLimit,
                                  @Value("${app.invitation-wave.max-recipients:5000}") int maxRecipients) {
        if (globalDailyLimit < 1) {
            throw new IllegalArgumentException("The global daily limit must be positive");
        }
        if (maxRecipients < 1) {
            throw new IllegalArgumentException("The invitation wave max recipients must be positive");
        }
        this.globalDailyLimit = globalDailyLimit;
        this.maxRecipients = maxRecipients;
    }

    public int globalDailyLimit() {
        return globalDailyLimit;
    }

    public int maxRecipients() {
        return maxRecipients;
    }
}
