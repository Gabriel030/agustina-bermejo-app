package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.api.CollectionReminderEmailSettings;
import com.red_escuela.backend.billing.api.CollectionReminderEmailSettingsQuery;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class StoredCollectionReminderEmailSettingsQuery implements CollectionReminderEmailSettingsQuery {

    private final BillingConfigurationReader configurationReader;

    public StoredCollectionReminderEmailSettingsQuery(BillingConfigurationReader configurationReader) {
        this.configurationReader = configurationReader;
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionReminderEmailSettings settingsEffectiveOn(LocalDate date) {
        CollectionReminderSettings settings = configurationReader.collectionReminderSettingsEffectiveOn(date);
        return new CollectionReminderEmailSettings(settings.emailWindowStart(), settings.emailWindowEnd(), settings.dailyEmailCap());
    }
}
