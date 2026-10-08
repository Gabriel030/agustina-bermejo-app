package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.policy.AnnualUpfrontCheck;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnnualUpfrontScopeHandler implements ConfigurationScopeHandler {
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final ConfigurationPolicyEvaluator policyEvaluator;

    public AnnualUpfrontScopeHandler(ProgramYearBillingTemplateRepository templateRepository, ConfigurationPolicyEvaluator policyEvaluator) {
        this.templateRepository = templateRepository;
        this.policyEvaluator = policyEvaluator;
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.ANNUAL_UPFRONT;
    }

    @Override
    public List<String> validationMoments() {
        return List.of("ANNUAL_UPFRONT_SETTINGS");
    }

    @Override
    public void requireCoherentValues(ConfigurationTopic topic, ConfigurationValues values) {
        var termStart = templateRepository.findByProgramIdAndTermId(topic.programId(), topic.termId())
                .orElseThrow(() -> new ProgramYearBillingTemplateNotFoundException(topic.programId()))
                .termStartDate().toLocalDate();
        policyEvaluator.requireCoherentAnnualUpfrontSettings(new AnnualUpfrontCheck((AnnualUpfrontSettings) values, termStart));
    }
}
