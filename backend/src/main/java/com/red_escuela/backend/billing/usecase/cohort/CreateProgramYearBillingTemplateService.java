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
import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.service.DirectConfigurationPublisher;
import com.red_escuela.backend.billing.configuration.service.YearBillingTermsOverlay;
import java.time.Clock;
import java.time.LocalDate;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import com.red_escuela.backend.billing.configuration.service.ConfigurationPolicyEvaluator;
import com.red_escuela.backend.billing.configuration.service.YearBillingConfigurationChangeLoader;
import com.red_escuela.backend.billing.configuration.service.YearBillingConfigurationDefaults;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.service.YearBillingConfiguredEvent;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateProgramYearBillingTemplateService implements CreateProgramYearBillingTemplateUseCase {
    private static final String INITIAL_VERSION_REASON = "Versión inicial del año lectivo";
    private static final int INITIAL_PAYMENT_DEADLINE_HOURS = 48;

    private final ProgramYearBillingTemplateRepository templateRepository;
    private final ProgramRepository programRepository;
    private final TermRepository termRepository;
    private final CurrentPersonService currentPersonService;
    private final YearBillingTermsOverlay termsOverlay;
    private final ApplicationEventPublisher eventPublisher;
    private final YearBillingConfigurationChangeLoader changeLoader;
    private final ConfigurationPolicyEvaluator policyEvaluator;
    private final YearBillingConfigurationDefaults configurationDefaults;
    private final DirectConfigurationPublisher publisher;
    private final Clock clock;

    public CreateProgramYearBillingTemplateService(ProgramYearBillingTemplateRepository templateRepository, ProgramRepository programRepository,
                                                     TermRepository termRepository, CurrentPersonService currentPersonService,
                                                     YearBillingTermsOverlay termsOverlay, ApplicationEventPublisher eventPublisher,
                                                     YearBillingConfigurationChangeLoader changeLoader, ConfigurationPolicyEvaluator policyEvaluator,
                                                     YearBillingConfigurationDefaults configurationDefaults, DirectConfigurationPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
        this.configurationDefaults = configurationDefaults;
        this.changeLoader = changeLoader;
        this.policyEvaluator = policyEvaluator;
        this.termsOverlay = termsOverlay;
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
        template.setCreatedBy(actor);
        ProgramYearBillingTemplate saved = templateRepository.save(template);
        publishInitialVersions(saved, term, termsOverlay.overlay(YearBillingTerms.initial(recurringFeeInvoicesCount), effectiveConfiguration), actor.getId());
        eventPublisher.publishEvent(YearBillingConfiguredEvent.configuredFor(programId));
        return saved;
    }

    private void publishInitialVersions(ProgramYearBillingTemplate template, Term term, YearBillingTerms terms, Long actorId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate termStart = term.getStartDate() == null ? today : term.getStartDate().toLocalDate();
        LocalDate effectiveFrom = termStart.isBefore(today) ? termStart : today;
        publisher.publishVersion(ConfigurationTopic.ofYearTerms(template.programId(), term.getId()), terms, effectiveFrom, INITIAL_VERSION_REASON, actorId);
        if (term.getStartDate() != null) {
            publisher.publishVersion(ConfigurationTopic.ofAnnualUpfront(template.programId(), term.getId()),
                    new AnnualUpfrontSettings(termStart.minusDays(1), INITIAL_PAYMENT_DEADLINE_HOURS), effectiveFrom, INITIAL_VERSION_REASON, actorId);
        }
    }
}
