package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;

import java.util.List;

public interface ConfigurationScopeHandler {
    ConfigurationScope scope();

    List<String> validationMoments();

    void requireCoherentValues(ConfigurationTopic topic, ConfigurationValues values);
}
