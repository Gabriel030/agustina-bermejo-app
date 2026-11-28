package com.red_escuela.backend.billing.cohort.service;

import com.red_escuela.backend.billing.configuration.domain.CohortEntryDiscount;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.configuration.service.ChangeBillingConfigurationOperation;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.api.ProgramCohortFinder;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class UpdateCohortEntryFeeDiscountTransaction {
    private static final String CHANGE_REASON = "Cambio del descuento de ingreso de la cohorte";

    private final ProgramCohortFinder programCohortFinder;
    private final BillingOperationPipeline pipeline;
    private final ChangeBillingConfigurationOperation operation;
    private final BillingActorResolver actorResolver;
    private final BillingConfigurationReader configurationReader;
    private final Clock clock;

    public UpdateCohortEntryFeeDiscountTransaction(ProgramCohortFinder programCohortFinder, BillingOperationPipeline pipeline, ChangeBillingConfigurationOperation operation, BillingActorResolver actorResolver, BillingConfigurationReader configurationReader, Clock clock) {
        this.programCohortFinder = programCohortFinder;
        this.pipeline = pipeline;
        this.operation = operation;
        this.actorResolver = actorResolver;
        this.configurationReader = configurationReader;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = AuthorizationPendingException.class)
    public ProgramCohort update(Long cohortId, Percentage discount) {
        ProgramCohort cohort = programCohortFinder.findOrThrow(cohortId);
        LocalDate today = LocalDate.now(clock);
        if (!discount.equals(configurationReader.cohortEntryDiscountEffectiveOn(cohortId, today))) {
            pipeline.runOperation(operation, new ChangeBillingConfigurationCommand(ConfigurationTopic.ofCohortEntry(cohortId), new CohortEntryDiscount(discount),
                            today, CHANGE_REASON, actorResolver.currentPersonIdOr(cohort.getCreatedBy().getId()), actorResolver.currentActorIsDirector()))
                    .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
        }
        return cohort;
    }
}
