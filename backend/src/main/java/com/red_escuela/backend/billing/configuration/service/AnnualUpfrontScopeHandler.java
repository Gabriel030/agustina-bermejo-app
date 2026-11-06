package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.policy.AnnualUpfrontCheck;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class AnnualUpfrontScopeHandler implements ConfigurationScopeHandler {
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final ConfigurationPolicyEvaluator policyEvaluator;
    private final BillingConfigurationReader configurationReader;
    private final Clock clock;

    public AnnualUpfrontScopeHandler(ProgramYearBillingTemplateRepository templateRepository, ConfigurationPolicyEvaluator policyEvaluator,
                                     BillingConfigurationReader configurationReader, Clock clock) {
        this.templateRepository = templateRepository;
        this.policyEvaluator = policyEvaluator;
        this.configurationReader = configurationReader;
        this.clock = clock;
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
        LocalDate firstInstallmentDueDate = configurationReader.findYearTermsEffectiveOn(topic.programId(), topic.termId(), LocalDate.now(clock))
                .map(YearBillingTerms::recurringFeeDueDays)
                .map(dueDays -> termStart.withDayOfMonth(1).plusDays(dueDays))
                .orElse(termStart);
        policyEvaluator.requireCoherentAnnualUpfrontSettings(new AnnualUpfrontCheck((AnnualUpfrontSettings) values, termStart, firstInstallmentDueDate));
    }
}
