package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.billing.pricing.policy.ChangePriceCommand;
import com.red_escuela.backend.billing.pricing.service.PriceChangeValidator;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PriceChangeViolationsReader {
    private final PriceChangeValidator validator;
    private final BillingActorResolver actorResolver;

    public PriceChangeViolationsReader(PriceChangeValidator validator, BillingActorResolver actorResolver) {
        this.validator = validator;
        this.actorResolver = actorResolver;
    }

    @Transactional(readOnly = true)
    public List<PolicyViolation> validatePriceChange(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount marketingDiscount, LocalDate effectiveFrom, String changeReason, Integer appliesToLectiveYear) {
        return validator.collectViolations(new ChangePriceCommand(priceId, baseAmount, marketingDiscount, effectiveFrom, changeReason, actorResolver.currentPersonId(), actorResolver.currentActorIsDirector(), null, appliesToLectiveYear));
    }
}
