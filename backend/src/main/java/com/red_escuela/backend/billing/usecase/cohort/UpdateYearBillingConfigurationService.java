package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.configuration.service.YearBillingTermsOverlay;
import com.red_escuela.backend.billing.configuration.usecase.ProposeBillingConfigurationChangeUseCase;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.billing.service.YearBillingConfiguredEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@UseCase
public class UpdateYearBillingConfigurationService implements UpdateYearBillingConfigurationUseCase {
    private static final String CHANGE_REASON = "Cambio de la configuración del año lectivo";

    private final ProgramYearBillingTemplateRepository templateRepository;
    private final BillingConfigurationReader configurationReader;
    private final YearBillingTermsOverlay termsOverlay;
    private final ProposeBillingConfigurationChangeUseCase proposeChangeUseCase;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public UpdateYearBillingConfigurationService(ProgramYearBillingTemplateRepository templateRepository, BillingConfigurationReader configurationReader,
                                                 YearBillingTermsOverlay termsOverlay, ProposeBillingConfigurationChangeUseCase proposeChangeUseCase,
                                                 ApplicationEventPublisher eventPublisher, Clock clock) {
        this.templateRepository = templateRepository;
        this.configurationReader = configurationReader;
        this.termsOverlay = termsOverlay;
        this.proposeChangeUseCase = proposeChangeUseCase;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProgramYearBillingTemplate update(Long templateId, YearBillingConfigurationRequest request) {
        ProgramYearBillingTemplate template = templateRepository.findByIdForUpdate(templateId)
                .orElseThrow(() -> new ProgramYearBillingTemplateNotFoundException(templateId));
        LocalDate today = LocalDate.now(clock);
        Long termId = template.getTerm().getId();
        YearBillingTerms proposed = termsOverlay.overlay(configurationReader.requireYearTermsEffectiveOn(template.programId(), termId, today), request);
        proposeChangeUseCase.proposeChange(ConfigurationTopic.ofYearTerms(template.programId(), termId), proposed, today, CHANGE_REASON)
                .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
        eventPublisher.publishEvent(YearBillingConfiguredEvent.configuredFor(template.getProgram().getId()));
        return template;
    }
}
