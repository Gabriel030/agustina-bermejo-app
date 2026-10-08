package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.policy.CancelConfigurationVersionCommand;
import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.configuration.service.CancelConfigurationVersionOperation;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class CancelScheduledConfigurationVersionService implements CancelScheduledConfigurationVersionUseCase {
    private final BillingOperationPipeline pipeline;
    private final CancelConfigurationVersionOperation operation;
    private final BillingActorResolver actorResolver;

    public CancelScheduledConfigurationVersionService(BillingOperationPipeline pipeline, CancelConfigurationVersionOperation operation,
                                                      BillingActorResolver actorResolver) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.actorResolver = actorResolver;
    }

    @Override
    @PreAuthorize("hasRole('DIRECTOR')")
    public ConfigurationVersion cancelScheduledVersion(Long configurationVersionId, String reason) {
        return pipeline.runOperation(operation, new CancelConfigurationVersionCommand(configurationVersionId, reason, actorResolver.currentPersonId()))
                .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }
}
