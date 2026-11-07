package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.billing.pricing.service.PriceChangeViolationsReader;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class ValidatePriceChangeService implements ValidatePriceChangeUseCase {

    private final PriceChangeViolationsReader priceChangeViolationsReader;

    public ValidatePriceChangeService(PriceChangeViolationsReader priceChangeViolationsReader) {
        this.priceChangeViolationsReader = priceChangeViolationsReader;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<PolicyViolation> validatePriceChange(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount marketingDiscount, LocalDate effectiveFrom, String changeReason, Integer appliesToLectiveYear) {
        return priceChangeViolationsReader.validatePriceChange(priceId, baseAmount, marketingDiscount, effectiveFrom, changeReason, appliesToLectiveYear);
    }
}
