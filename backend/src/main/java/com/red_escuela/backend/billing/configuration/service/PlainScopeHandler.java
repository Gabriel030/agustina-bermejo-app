package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;

import java.util.List;

public class PlainScopeHandler implements ConfigurationScopeHandler {
    private final ConfigurationScope scope;

    public PlainScopeHandler(ConfigurationScope scope) {
        this.scope = scope;
    }

    @Override
    public ConfigurationScope scope() {
        return scope;
    }

    @Override
    public List<String> validationMoments() {
        return List.of();
    }

    @Override
    public void requireCoherentValues(ConfigurationTopic topic, ConfigurationValues values) {
    }
}
