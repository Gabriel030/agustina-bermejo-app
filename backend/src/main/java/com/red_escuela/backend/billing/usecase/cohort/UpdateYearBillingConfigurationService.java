package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.billing.service.ProgramYearBillingTemplateService;
import com.red_escuela.backend.billing.service.YearBillingConfigurationApplier;
import com.red_escuela.backend.billing.service.YearBillingConfiguredEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class UpdateYearBillingConfigurationService implements UpdateYearBillingConfigurationUseCase {
    private final ProgramYearBillingTemplateService templateService;
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final YearBillingConfigurationApplier configurationApplier;
    private final ApplicationEventPublisher eventPublisher;

    public UpdateYearBillingConfigurationService(ProgramYearBillingTemplateService templateService,
                                                 ProgramYearBillingTemplateRepository templateRepository,
                                                 YearBillingConfigurationApplier configurationApplier, ApplicationEventPublisher eventPublisher) {
        this.templateService = templateService;
        this.templateRepository = templateRepository;
        this.configurationApplier = configurationApplier;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ProgramYearBillingTemplate update(Long templateId, YearBillingConfigurationRequest request) {
        ProgramYearBillingTemplate template = templateService.findOrThrow(templateId);
        configurationApplier.apply(template, request);
        ProgramYearBillingTemplate saved = templateRepository.save(template);
        eventPublisher.publishEvent(new YearBillingConfiguredEvent(template.getProgram().getId()));
        return saved;
    }
}
