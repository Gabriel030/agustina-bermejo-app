package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.IssuanceSettings;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IssuanceScopeHandler implements ConfigurationScopeHandler {
    private final ConfigurationPolicyEvaluator policyEvaluator;

    public IssuanceScopeHandler(ConfigurationPolicyEvaluator policyEvaluator) {
        this.policyEvaluator = policyEvaluator;
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.ISSUANCE;
    }

    @Override
    public List<String> validationMoments() {
        return List.of("ISSUANCE_SETTINGS");
    }

    @Override
    public void requireCoherentValues(ConfigurationTopic topic, ConfigurationValues values) {
        policyEvaluator.requireCoherentIssuanceSettings((IssuanceSettings) values);
    }
}
