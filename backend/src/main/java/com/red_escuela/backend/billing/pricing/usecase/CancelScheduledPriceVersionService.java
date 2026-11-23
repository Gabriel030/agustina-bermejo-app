package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.policy.CancelPriceVersionCommand;
import com.red_escuela.backend.billing.pricing.service.CancelPriceVersionOperation;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CancelScheduledPriceVersionService implements CancelScheduledPriceVersionUseCase {
    private final BillingOperationPipeline pipeline;
    private final CancelPriceVersionOperation operation;
    private final BillingActorResolver actorResolver;

    public CancelScheduledPriceVersionService(BillingOperationPipeline pipeline, CancelPriceVersionOperation operation, BillingActorResolver actorResolver) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.actorResolver = actorResolver;
    }

    @Override
    public PriceVersion cancelScheduledVersion(Long priceVersionId, String reason) {
        return pipeline.runOperation(operation, new CancelPriceVersionCommand(priceVersionId, reason, actorResolver.currentPersonId()))
                .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }
}
