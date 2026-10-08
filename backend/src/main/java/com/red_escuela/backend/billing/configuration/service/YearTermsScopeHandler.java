package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.policy.YearBillingConfigurationChange;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class YearTermsScopeHandler implements ConfigurationScopeHandler {
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final YearBillingConfigurationChangeLoader changeLoader;
    private final ConfigurationPolicyEvaluator policyEvaluator;

    public YearTermsScopeHandler(ProgramYearBillingTemplateRepository templateRepository, YearBillingConfigurationChangeLoader changeLoader,
                                 ConfigurationPolicyEvaluator policyEvaluator) {
        this.templateRepository = templateRepository;
        this.changeLoader = changeLoader;
        this.policyEvaluator = policyEvaluator;
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.YEAR_TERMS;
    }

    @Override
    public List<String> validationMoments() {
        return List.of("YEAR_BILLING_CONFIGURATION", "YEAR_BILLING_CONFIGURATION_FREEZE");
    }

    @Override
    public void requireCoherentValues(ConfigurationTopic topic, ConfigurationValues values) {
        YearBillingConfigurationChange change = changeLoader.loadVersionedChange(
                templateRepository.findByProgramIdAndTermId(topic.programId(), topic.termId())
                        .orElseThrow(() -> new ProgramYearBillingTemplateNotFoundException(topic.programId())),
                (YearBillingTerms) values);
        policyEvaluator.requireCoherentYearConfiguration(change);
        policyEvaluator.requireUnfrozenYearConfiguration(change);
    }
}
