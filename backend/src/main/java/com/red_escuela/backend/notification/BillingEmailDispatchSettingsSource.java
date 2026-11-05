package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.api.CollectionReminderEmailSettings;
import com.red_escuela.backend.billing.api.CollectionReminderEmailSettingsQuery;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchSettings;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchWindow;
import com.red_escuela.backend.notification.delivery.service.EmailDispatchSettingsSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class BillingEmailDispatchSettingsSource implements EmailDispatchSettingsSource {

    private final CollectionReminderEmailSettingsQuery settingsQuery;

    public BillingEmailDispatchSettingsSource(CollectionReminderEmailSettingsQuery settingsQuery) {
        this.settingsQuery = settingsQuery;
    }

    @Override
    public EmailDispatchSettings settingsEffectiveOn(LocalDate date) {
        CollectionReminderEmailSettings settings = settingsQuery.settingsEffectiveOn(date);
        return new EmailDispatchSettings(new EmailDispatchWindow(settings.windowStart(), settings.windowEnd()), settings.dailyCap());
    }
}
