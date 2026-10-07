package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.configuration.service.ChangeBillingConfigurationOperation;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;

@UseCase
public class ProposeBillingConfigurationChangeService implements ProposeBillingConfigurationChangeUseCase {
    private final BillingOperationPipeline pipeline;
    private final ChangeBillingConfigurationOperation operation;
    private final BillingActorResolver actorResolver;

    public ProposeBillingConfigurationChangeService(BillingOperationPipeline pipeline, ChangeBillingConfigurationOperation operation,
                                                    BillingActorResolver actorResolver) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.actorResolver = actorResolver;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public BillingOperationOutcome<ConfigurationVersion> proposeChange(ConfigurationTopic topic, ConfigurationValues values, LocalDate effectiveFrom, String changeReason) {
        ChangeBillingConfigurationCommand command = new ChangeBillingConfigurationCommand(topic, values, effectiveFrom, changeReason,
                actorResolver.currentPersonId(), actorResolver.currentActorIsDirector());
        return pipeline.runOperation(operation, command);
    }
}
