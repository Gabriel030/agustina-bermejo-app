package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.configuration.policy.YearBillingConfigurationChange;
import com.red_escuela.backend.billing.configuration.service.ConfigurationPolicyEvaluator;
import com.red_escuela.backend.billing.configuration.service.YearBillingConfigurationChangeLoader;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.service.YearBillingConfigurationApplier;
import com.red_escuela.backend.billing.service.YearBillingConfiguredEvent;
import com.red_escuela.backend.billing.configuration.service.LegacyConfigurationMirror;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class UpdateYearBillingConfigurationService implements UpdateYearBillingConfigurationUseCase {
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final YearBillingConfigurationApplier configurationApplier;
    private final ApplicationEventPublisher eventPublisher;
    private final YearBillingConfigurationChangeLoader changeLoader;
    private final ConfigurationPolicyEvaluator policyEvaluator;
    private final LegacyConfigurationMirror configurationMirror;
    private final CurrentPersonService currentPersonService;

    public UpdateYearBillingConfigurationService(ProgramYearBillingTemplateRepository templateRepository,
                                                 YearBillingConfigurationApplier configurationApplier, ApplicationEventPublisher eventPublisher,
                                                 YearBillingConfigurationChangeLoader changeLoader, ConfigurationPolicyEvaluator policyEvaluator,
                                                 LegacyConfigurationMirror configurationMirror, CurrentPersonService currentPersonService) {
        this.configurationMirror = configurationMirror;
        this.currentPersonService = currentPersonService;
        this.changeLoader = changeLoader;
        this.policyEvaluator = policyEvaluator;
        this.templateRepository = templateRepository;
        this.configurationApplier = configurationApplier;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ProgramYearBillingTemplate update(Long templateId, YearBillingConfigurationRequest request) {
        ProgramYearBillingTemplate template = templateRepository.findByIdForUpdate(templateId)
                .orElseThrow(() -> new ProgramYearBillingTemplateNotFoundException(templateId));
        YearBillingConfigurationChange change = changeLoader.loadConfigurationUpdate(template, request);
        policyEvaluator.requireCoherentYearConfiguration(change);
        policyEvaluator.requireUnfrozenYearConfiguration(change);
        configurationApplier.applyYearBillingConfiguration(template, request);
        ProgramYearBillingTemplate saved = templateRepository.save(template);
        configurationMirror.recordYearTerms(saved, currentPersonService.resolveCurrentPerson().getId());
        eventPublisher.publishEvent(YearBillingConfiguredEvent.configuredFor(template.getProgram().getId()));
        return saved;
    }
}
