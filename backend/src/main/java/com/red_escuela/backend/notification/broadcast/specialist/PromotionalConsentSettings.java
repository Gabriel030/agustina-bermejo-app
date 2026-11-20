package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.exception.PromotionalConsentDisabledException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PromotionalConsentSettings {

    private final boolean enabled;

    public PromotionalConsentSettings(@Value("${app.consent.promotional-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public void requireAllowed(BroadcastCategory category, boolean granted) {
        if (category == BroadcastCategory.PROMOTIONAL && granted && !enabled) {
            throw new PromotionalConsentDisabledException();
        }
    }
}
