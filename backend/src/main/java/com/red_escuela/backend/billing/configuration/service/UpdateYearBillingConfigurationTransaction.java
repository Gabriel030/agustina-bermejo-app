package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.billing.service.YearBillingConfiguredEvent;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class UpdateYearBillingConfigurationTransaction {
    private static final String CHANGE_REASON = "Cambio de la configuración del año lectivo";

    private final ProgramYearBillingTemplateRepository templateRepository;
    private final BillingConfigurationReader configurationReader;
    private final YearBillingTermsOverlay termsOverlay;
    private final BillingOperationPipeline pipeline;
    private final ChangeBillingConfigurationOperation operation;
    private final BillingActorResolver actorResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public UpdateYearBillingConfigurationTransaction(ProgramYearBillingTemplateRepository templateRepository, BillingConfigurationReader configurationReader,
                                                 YearBillingTermsOverlay termsOverlay, BillingOperationPipeline pipeline, ChangeBillingConfigurationOperation operation, BillingActorResolver actorResolver,
                                                 ApplicationEventPublisher eventPublisher, Clock clock) {
        this.templateRepository = templateRepository;
        this.configurationReader = configurationReader;
        this.termsOverlay = termsOverlay;
        this.pipeline = pipeline;
        this.operation = operation;
        this.actorResolver = actorResolver;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = AuthorizationPendingException.class)
    public ProgramYearBillingTemplate update(Long templateId, YearBillingConfigurationRequest request) {
        ProgramYearBillingTemplate template = templateRepository.findByIdForUpdate(templateId)
                .orElseThrow(() -> new ProgramYearBillingTemplateNotFoundException(templateId));
        LocalDate today = LocalDate.now(clock);
        Long termId = template.termId();
        YearBillingTerms current = configurationReader.requireYearTermsEffectiveOn(template.programId(), termId, today);
        YearBillingTerms proposed = termsOverlay.overlay(current, request);
        if (!proposed.equals(current)) {
            pipeline.runOperation(operation, new ChangeBillingConfigurationCommand(ConfigurationTopic.ofYearTerms(template.programId(), termId), proposed, today, CHANGE_REASON,
                            actorResolver.currentPersonIdOr(templateRepository.findCreatedByIdById(templateId)), actorResolver.currentActorIsDirector()))
                    .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
        }
        eventPublisher.publishEvent(YearBillingConfiguredEvent.configuredFor(template.getProgram().getId()));
        return template;
    }
}
