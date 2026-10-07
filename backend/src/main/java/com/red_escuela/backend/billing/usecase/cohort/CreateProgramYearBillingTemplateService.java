package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import com.red_escuela.backend.billing.configuration.service.ConfigurationPolicyEvaluator;
import com.red_escuela.backend.billing.configuration.service.YearBillingConfigurationChangeLoader;
import com.red_escuela.backend.billing.configuration.service.YearBillingConfigurationDefaults;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.service.YearBillingConfigurationApplier;
import com.red_escuela.backend.billing.service.YearBillingConfiguredEvent;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateProgramYearBillingTemplateService implements CreateProgramYearBillingTemplateUseCase {
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final ProgramRepository programRepository;
    private final TermRepository termRepository;
    private final CurrentPersonService currentPersonService;
    private final YearBillingConfigurationApplier configurationApplier;
    private final ApplicationEventPublisher eventPublisher;
    private final YearBillingConfigurationChangeLoader changeLoader;
    private final ConfigurationPolicyEvaluator policyEvaluator;
    private final YearBillingConfigurationDefaults configurationDefaults;

    public CreateProgramYearBillingTemplateService(ProgramYearBillingTemplateRepository templateRepository, ProgramRepository programRepository,
                                                     TermRepository termRepository, CurrentPersonService currentPersonService,
                                                     YearBillingConfigurationApplier configurationApplier, ApplicationEventPublisher eventPublisher,
                                                     YearBillingConfigurationChangeLoader changeLoader, ConfigurationPolicyEvaluator policyEvaluator,
                                                     YearBillingConfigurationDefaults configurationDefaults) {
        this.configurationDefaults = configurationDefaults;
        this.changeLoader = changeLoader;
        this.policyEvaluator = policyEvaluator;
        this.configurationApplier = configurationApplier;
        this.eventPublisher = eventPublisher;
        this.templateRepository = templateRepository;
        this.programRepository = programRepository;
        this.termRepository = termRepository;
        this.currentPersonService = currentPersonService;
    }

    @Override
    public ProgramYearBillingTemplate create(Long programId, Long termId, Integer recurringFeeInvoicesCount) {
        return create(programId, termId, recurringFeeInvoicesCount, null);
    }

    @Override
    @Transactional
    public ProgramYearBillingTemplate create(Long programId, Long termId, Integer recurringFeeInvoicesCount, YearBillingConfigurationRequest configuration) {
        Program program = programRepository.findById(programId).orElseThrow(() -> new ProgramNotFoundException(programId));
        Term term = termRepository.findById(termId).orElseThrow(() -> new TermNotFoundException(termId));
        Person actor = currentPersonService.resolveCurrentPerson();
        YearBillingConfigurationRequest effectiveConfiguration = configurationDefaults.completeForCreation(term.getStartDate(), configuration);
        policyEvaluator.requireCoherentYearConfiguration(changeLoader.loadCreation(term.getStartDate(), term.getEndDate(), recurringFeeInvoicesCount, effectiveConfiguration));

        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        template.setRecurringFeeInvoicesCount(recurringFeeInvoicesCount);
        template.setCreatedBy(actor);
        configurationApplier.applyYearBillingConfiguration(template, effectiveConfiguration);
        ProgramYearBillingTemplate saved = templateRepository.save(template);
        eventPublisher.publishEvent(YearBillingConfiguredEvent.configuredFor(programId));
        return saved;
    }
}
