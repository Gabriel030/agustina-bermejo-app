package com.red_escuela.backend.billing.api;

import java.time.LocalDate;

public interface CollectionReminderEmailSettingsQuery {

    CollectionReminderEmailSettings settingsEffectiveOn(LocalDate date);
}
