package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.billing.configuration.exception.NoEffectiveConfigurationException;

import java.time.LocalDate;
import java.util.Optional;

public final class EffectiveConfigurations {
    private EffectiveConfigurations() {
    }

    public static <V extends ConfigurationValues> V requireInEffect(Optional<V> values, ConfigurationTopic topic, LocalDate date) {
        return values.orElseThrow(() -> new NoEffectiveConfigurationException(topic, date));
    }
}
