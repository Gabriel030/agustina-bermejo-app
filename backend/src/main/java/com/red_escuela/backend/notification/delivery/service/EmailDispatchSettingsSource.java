package com.red_escuela.backend.notification.delivery.service;

import com.red_escuela.backend.notification.delivery.domain.EmailDispatchSettings;

import java.time.LocalDate;

public interface EmailDispatchSettingsSource {

    EmailDispatchSettings settingsEffectiveOn(LocalDate date);
}
