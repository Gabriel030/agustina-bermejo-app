package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.policy.CollectionReminderEffectiveFromCheck;
import com.red_escuela.backend.billing.configuration.policy.ReminderScheduleCheck;

import java.time.LocalDate;
import java.util.List;

abstract class ReminderScheduleScopeHandler implements ConfigurationScopeHandler {
    private final ConfigurationPolicyEvaluator policyEvaluator;

    ReminderScheduleScopeHandler(ConfigurationPolicyEvaluator policyEvaluator) {
        this.policyEvaluator = policyEvaluator;
    }

    abstract ReminderSchedule scheduleOf(ConfigurationValues values);

    @Override
    public List<String> validationMoments() {
        return List.of("COLLECTION_REMINDER_SCHEDULE", "COLLECTION_REMINDER_SCHEDULE_CHANGE", "COLLECTION_REMINDER_EFFECTIVE_FROM");
    }

    @Override
    public void requireCoherentValues(ConfigurationTopic topic, ConfigurationValues values) {
        policyEvaluator.requireCoherentCollectionReminderSchedule(new ReminderScheduleCheck(scheduleOf(values), topic.isReminderScheduleException()));
    }

    @Override
    public void requireAllowedEffectiveFrom(ConfigurationTopic topic, LocalDate effectiveFrom, LocalDate today) {
        policyEvaluator.requireCollectionReminderEffectiveFromNextDay(new CollectionReminderEffectiveFromCheck(effectiveFrom, today));
    }
}
