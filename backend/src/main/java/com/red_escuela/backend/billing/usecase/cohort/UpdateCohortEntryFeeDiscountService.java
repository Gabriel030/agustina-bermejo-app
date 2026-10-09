package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.configuration.domain.CohortEntryDiscount;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.configuration.service.ChangeBillingConfigurationOperation;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@UseCase
public class UpdateCohortEntryFeeDiscountService implements UpdateCohortEntryFeeDiscountUseCase {
    private static final String CHANGE_REASON = "Cambio del descuento de ingreso de la cohorte";

    private final ProgramCohortRepository programCohortRepository;
    private final BillingOperationPipeline pipeline;
    private final ChangeBillingConfigurationOperation operation;
    private final BillingActorResolver actorResolver;
    private final Clock clock;

    public UpdateCohortEntryFeeDiscountService(ProgramCohortRepository programCohortRepository, BillingOperationPipeline pipeline, ChangeBillingConfigurationOperation operation, BillingActorResolver actorResolver, Clock clock) {
        this.programCohortRepository = programCohortRepository;
        this.pipeline = pipeline;
        this.operation = operation;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = AuthorizationPendingException.class)
    public ProgramCohort update(Long cohortId, Percentage discount) {
        ProgramCohort cohort = programCohortRepository.findById(cohortId).orElseThrow(() -> new ProgramCohortNotFoundException(cohortId));
        pipeline.runOperation(operation, new ChangeBillingConfigurationCommand(ConfigurationTopic.ofCohortEntry(cohortId), new CohortEntryDiscount(discount),
                        LocalDate.now(clock), CHANGE_REASON, actorResolver.currentPersonIdOr(cohort.getCreatedBy().getId()), actorResolver.currentActorIsDirector()))
                .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
        return cohort;
    }
}
