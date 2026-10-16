package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.billing.pricing.policy.ChangePriceCommand;
import com.red_escuela.backend.billing.pricing.service.PriceChangeValidator;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@UseCase
public class ValidatePriceChangeService implements ValidatePriceChangeUseCase {
    private final PriceChangeValidator validator;
    private final BillingActorResolver actorResolver;

    public ValidatePriceChangeService(PriceChangeValidator validator, BillingActorResolver actorResolver) {
        this.validator = validator;
        this.actorResolver = actorResolver;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('DIRECTOR')")
    public List<PolicyViolation> validatePriceChange(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount marketingDiscount, LocalDate effectiveFrom, String changeReason) {
        return validator.collectViolations(new ChangePriceCommand(priceId, baseAmount, marketingDiscount, effectiveFrom, changeReason, actorResolver.currentPersonId()));
    }
}
