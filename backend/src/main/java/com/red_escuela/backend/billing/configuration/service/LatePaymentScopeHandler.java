package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LatePaymentScopeHandler implements ConfigurationScopeHandler {
    private final ConfigurationPolicyEvaluator policyEvaluator;

    public LatePaymentScopeHandler(ConfigurationPolicyEvaluator policyEvaluator) {
        this.policyEvaluator = policyEvaluator;
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.LATE_PAYMENT;
    }

    @Override
    public List<String> validationMoments() {
        return List.of("LATE_PAYMENT_TERMS");
    }

    @Override
    public void requireCoherentValues(ConfigurationTopic topic, ConfigurationValues values) {
        policyEvaluator.requireCoherentLatePaymentTerms((LatePaymentTerms) values);
    }
}
