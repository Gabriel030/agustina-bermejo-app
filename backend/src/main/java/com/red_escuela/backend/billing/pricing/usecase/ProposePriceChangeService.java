package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
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
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public BillingOperationOutcome<PriceVersion> proposeOrPublishPriceVersion(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount marketingDiscount, LocalDate effectiveFrom, String changeReason) {
        ChangePriceCommand command = new ChangePriceCommand(priceId, baseAmount, marketingDiscount, effectiveFrom, changeReason, actorResolver.currentPersonId(),
                actorResolver.currentActorIsDirector());
        return pipeline.runOperation(operation, command);
    }
}
