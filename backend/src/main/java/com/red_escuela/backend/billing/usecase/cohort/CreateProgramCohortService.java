package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.billing.service.ProgramYearBillingTemplateService;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.billing.configuration.domain.CohortEntryDiscount;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.configuration.service.ChangeBillingConfigurationOperation;
import com.red_escuela.backend.billing.configuration.service.DirectConfigurationPublisher;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.domain.Percentage;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateProgramCohortService implements CreateProgramCohortUseCase {
    private static final String INITIAL_VERSION_REASON = "Descuento de ingreso inicial de la cohorte";
    private static final String PROPOSAL_REASON = "Descuento de ingreso solicitado al crear la cohorte";

    private final ProgramCohortRepository programCohortRepository;
    private final ProgramYearBillingTemplateService templateService;
    private final CurrentPersonService currentPersonService;
    private final DirectConfigurationPublisher publisher;
    private final BillingOperationPipeline pipeline;
    private final ChangeBillingConfigurationOperation operation;
    private final BillingActorResolver actorResolver;
    private final Clock clock;

    public CreateProgramCohortService(ProgramCohortRepository programCohortRepository, ProgramYearBillingTemplateService templateService,
                                       CurrentPersonService currentPersonService, DirectConfigurationPublisher publisher, BillingOperationPipeline pipeline,
                                      ChangeBillingConfigurationOperation operation, BillingActorResolver actorResolver, Clock clock) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.actorResolver = actorResolver;
        this.publisher = publisher;
        this.clock = clock;
        this.programCohortRepository = programCohortRepository;
        this.templateService = templateService;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional
    public ProgramCohort create(Long templateId, CreateProgramCohortRequest request) {
        ProgramYearBillingTemplate template = templateService.findOrThrow(templateId);
        Person actor = currentPersonService.resolveCurrentPerson();

        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplate(template);
        cohort.setIntake(request.intake());
        cohort.setAdmissionStart(request.admissionStart());
        cohort.setAdmissionEnd(request.admissionEnd());
        cohort.setCursadaStart(request.cursadaStart());
        cohort.setCreatedBy(actor);
        ProgramCohort saved = programCohortRepository.save(cohort);
        Percentage defaultDiscount = Percentage.ofFraction(request.intake() == CohortIntake.SECOND ? new BigDecimal("0.5") : BigDecimal.ZERO);
        Percentage requested = request.entryFeeDiscountPercent() != null ? request.entryFeeDiscountPercent() : defaultDiscount;
        LocalDate today = LocalDate.now(clock);
        ConfigurationTopic topic = ConfigurationTopic.ofCohortEntry(saved.getId());
        boolean proposalRequired = !requested.equals(defaultDiscount) && !actorResolver.currentActorIsDirector();
        Percentage published = proposalRequired ? defaultDiscount : requested;
        if (published.points().signum() > 0) {
            publisher.publishVersion(topic, new CohortEntryDiscount(published), today, INITIAL_VERSION_REASON, actor.getId());
        }
        if (proposalRequired) {
            pipeline.runOperation(operation, new ChangeBillingConfigurationCommand(topic, new CohortEntryDiscount(requested), today, PROPOSAL_REASON, actor.getId(), false));
        }
        return saved;
    }
}
