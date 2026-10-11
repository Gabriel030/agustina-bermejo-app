package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.policy.CollectionReminderEffectiveFromCheck;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class CollectionReminderScopeHandler implements ConfigurationScopeHandler {
    private final ConfigurationPolicyEvaluator policyEvaluator;

    public CollectionReminderScopeHandler(ConfigurationPolicyEvaluator policyEvaluator) {
        this.policyEvaluator = policyEvaluator;
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.COLLECTION_REMINDERS;
    }

    @Override
    public List<String> validationMoments() {
        return List.of("COLLECTION_REMINDER_SETTINGS", "COLLECTION_REMINDER_EFFECTIVE_FROM");
    }

    @Override
    public void requireCoherentValues(ConfigurationTopic topic, ConfigurationValues values) {
        policyEvaluator.requireCoherentCollectionReminderSettings((CollectionReminderSettings) values);
    }

    @Override
    public void requireAllowedEffectiveFrom(ConfigurationTopic topic, LocalDate effectiveFrom, LocalDate today) {
        policyEvaluator.requireCollectionReminderEffectiveFromNextDay(new CollectionReminderEffectiveFromCheck(effectiveFrom, today));
    }
}
