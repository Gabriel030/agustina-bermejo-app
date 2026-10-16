package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.policy.ChangePriceCommand;
import com.red_escuela.backend.billing.pricing.service.ChangePriceOperation;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;

@UseCase
public class ProposePriceChangeService implements ProposePriceChangeUseCase {
    private final BillingOperationPipeline pipeline;
    private final ChangePriceOperation operation;
    private final BillingActorResolver actorResolver;

    public ProposePriceChangeService(BillingOperationPipeline pipeline, ChangePriceOperation operation, BillingActorResolver actorResolver) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.actorResolver = actorResolver;
    }

    @Override
    @PreAuthorize("hasRole('DIRECTOR')")
    public PriceVersion publishPriceVersion(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount marketingDiscount, LocalDate effectiveFrom, String changeReason) {
        ChangePriceCommand command = new ChangePriceCommand(priceId, baseAmount, marketingDiscount, effectiveFrom, changeReason, actorResolver.currentPersonId());
        return pipeline.runOperation(operation, command).resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }
}
