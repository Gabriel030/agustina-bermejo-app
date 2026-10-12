package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.academicstructure.api.TermScheduleChangeValidator;
import com.red_escuela.backend.billing.configuration.policy.YearBillingConfigurationChange;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class BillingTermScheduleChangeValidator implements TermScheduleChangeValidator {
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final YearBillingConfigurationChangeLoader changeLoader;
    private final ConfigurationPolicyEvaluator policyEvaluator;

    public BillingTermScheduleChangeValidator(ProgramYearBillingTemplateRepository templateRepository,
                                              YearBillingConfigurationChangeLoader changeLoader, ConfigurationPolicyEvaluator policyEvaluator) {
        this.templateRepository = templateRepository;
        this.changeLoader = changeLoader;
        this.policyEvaluator = policyEvaluator;
    }

    @Override
    public void validateScheduleChange(Long termId, LocalDateTime currentStart, LocalDateTime currentEnd, LocalDateTime proposedStart, LocalDateTime proposedEnd) {
        if (Objects.equals(proposedStart, currentStart) && Objects.equals(proposedEnd, currentEnd)) {
            return;
        }
        List<YearBillingConfigurationChange> changes = templateRepository.findAllByTermId(termId).stream()
                .map(template -> changeLoader.loadTermScheduleChange(template, proposedStart, proposedEnd))
                .toList();
        changes.forEach(policyEvaluator::requireCoherentYearConfiguration);
        changes.forEach(policyEvaluator::requireUnfrozenYearConfiguration);
    }
}
